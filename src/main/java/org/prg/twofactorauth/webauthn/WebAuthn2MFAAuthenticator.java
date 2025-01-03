package org.prg.twofactorauth.webauthn;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.prg.twofactorauth.dto.LoginFinishRequest;
import org.prg.twofactorauth.webauthn.domain.DbUtil;
import org.prg.twofactorauth.webauthn.domain.UserService;
import org.prg.twofactorauth.webauthn.domain.UserServiceImpl;
import org.prg.twofactorauth.webauthn.entity.FidoCredentialEntity;

import java.util.List;
import java.util.Map;


public class WebAuthn2MFAAuthenticator extends AbstractUsernameFormAuthenticator {

    private static final Logger logger = Logger.getLogger(WebAuthn2MFAAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // No-op for the initial step; handled by REST endpoint
        challenge(context, null);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        logger.info("WebAuthn2MFAAuthenticator Action started");
        AuthenticationSessionModel session = context.getAuthenticationSession();
        String username = session.getAuthenticatedUser().getUsername();
        String credential = context.getHttpRequest()
                .getDecodedFormParameters()
                .getFirst("credential");
        String flowId = context.getHttpRequest()
                .getDecodedFormParameters()
                .getFirst("flowId");

        if (completeAuthentication(username, credential, flowId, context)) {
            context.success();
        } else {
            Response errorResponse = Response.status(Response.Status.UNAUTHORIZED)
                    .entity("WebAuthn authentication failed")
                    .build();
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, errorResponse);
        }
    }

    private boolean completeAuthentication(String username,
                                           String credential,
                                           String flowId,
                                           AuthenticationFlowContext context) {
        // Implement WebAuthn assertion validation logic
        // Use a WebAuthn library (e.g., Yubico's webauthn-server)
        try {
            logger.info("WebAuthn2MFAAuthenticator completeAuthentication validation ");

            KeycloakSession session = context.getSession();
            UserModel user = KeycloakModelUtils.findUserByNameOrEmail(session, context.getRealm(), username);
            UserService userService = new UserServiceImpl(session, user, DbUtil.getEntityManager(session));
            List<FidoCredentialEntity> credentialEntities = userService.findCredentialsByUserId(user.getId());
            if (credentialEntities.isEmpty()) {
                //no webauthn configured for the user and we can return success
                return true;
            }
            // webauthn configured and validation is required
            if (credential == null) {
                return false;
            }
            LoginFinishRequest request = new LoginFinishRequest();
            request.setFlowId(flowId);
            request.setCredential(credential);
            Map<String, Object> map = userService.finishLogin(request);
            return map.containsKey("success");
        } catch (Exception e) {
            logger.error("WebAuthn2MFAAuthenticator completeAuthentication " + e);
            return false;
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession keycloakSession,
                                 RealmModel realmModel, UserModel userModel) {

        UserService userService = new UserServiceImpl(keycloakSession, userModel, DbUtil.getEntityManager(keycloakSession));
        List<FidoCredentialEntity> credentialEntities = userService.findCredentialsByUserId(userModel.getId());
        logger.info("WebAuthn2MFAAuthenticator credentialEntities " + credentialEntities);
        return credentialEntities.isEmpty();
    }

    @Override
    public void setRequiredActions(KeycloakSession keycloakSession,
                                   RealmModel realmModel, UserModel userModel) {

    }


    @Override
    public void close() {
    }
}