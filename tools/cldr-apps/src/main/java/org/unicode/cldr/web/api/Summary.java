package org.unicode.cldr.web.api;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.web.CookieSession;
import org.unicode.cldr.web.Dashboard;
import org.unicode.cldr.web.Dashboard.ReviewOutput;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.VettingViewerQueue;

@Path("/summary")
@Tag(name = "voting", description = "APIs for voting")
public class Summary {

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get Priority Items Summary",
        description = "Also known as Vetting Summary, this like a Dashboard for multiple locales.")
    @APIResponses(
        value = {
            @APIResponse(
                responseCode = "200",
                description = "Results of Summary operation",
                content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SummaryResponse.class)))
        })
    public Response doVettingSummary(
        SummaryRequest request,
        @HeaderParam(Auth.SESSION_HEADER) String sessionString
    ) {
        try {
            CookieSession session = Auth.getSession(sessionString);
            if (session == null) {
                return Auth.noSessionResponse();
            }
            if (!UserRegistry.userCanUseVettingSummary(session.user)) {
                return Response.status(403, "Forbidden").build();
            }
            session.userDidAction();
            // End boilerplate

            VettingViewerQueue.Status status[] = new VettingViewerQueue.Status[1];
            StringBuilder sb = new StringBuilder();
            JSONObject jStatus = new JSONObject();
            VettingViewerQueue vvq = VettingViewerQueue.getInstance();
            String str = vvq.getPriorityItemsSummaryOutput(session, status, request.loadingPolicy, sb, jStatus);
            SummaryResponse resp = new SummaryResponse();
            resp.jStatus = jStatus;
            resp.status = status[0];
            resp.ret = str;
            resp.output = sb.toString();
            return Response.ok(resp, MediaType.APPLICATION_JSON).build();
        } catch (JSONException | IOException ioe) {
            return Response.status(500, "An exception occurred").entity(ioe).build();
        }
    }

    @GET
    @Path("/dashboard/{locale}/{level}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Fetch the Dashboard for a locale",
        description = "Given a locale, get the summary information, aka Dashboard"
    )
    @APIResponses(
        value = {
            @APIResponse(
                responseCode = "200",
                description = "Dashboard results",
                content =  @Content(mediaType = "application/json",
                schema = @Schema(implementation = ReviewOutput.class))) // TODO: SummaryResults.class
        }
    )
    public Response getDashboard(
        @PathParam("locale") @Schema(required = true, description = "Locale ID") String locale,
        @PathParam("level") @Schema(required = true, description = "Coverage Level") String level,
        @HeaderParam(Auth.SESSION_HEADER) String session) {
        CLDRLocale loc = CLDRLocale.getInstance(locale);
        CookieSession cs = Auth.getSession(session);
        if (cs == null) {
            return Auth.noSessionResponse();
        }
        if (!UserRegistry.userCanModifyLocale(cs.user, loc)) {
            return Response.status(403, "Forbidden").build();
        }
        cs.userDidAction();

        // *Beware*  org.unicode.cldr.util.Level (coverage) ≠ VoteResolver.Level (user)
        Level coverageLevel = org.unicode.cldr.util.Level.fromString(level);
        ReviewOutput ret = new Dashboard().get(loc, cs.user, coverageLevel);

        return Response.ok().entity(ret).build();
    }
}
