package com.azkar.controllers;

import com.azkar.entities.User;
import com.azkar.entities.User.UserFacebookData;
import com.azkar.payload.ResponseBase.Error;
import com.azkar.payload.authenticationcontroller.requests.FacebookAuthenticationBody;
import com.azkar.payload.authenticationcontroller.responses.FacebookAuthenticationResponse;
import com.azkar.repos.UserRepo;
import com.azkar.services.JwtService;
import com.azkar.services.UserService;
import java.io.UnsupportedEncodingException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthenticationController extends BaseController {

  public final static String LOGIN_WITH_FACEBOOK_PATH = "/login/facebook";
  public final static String CONNECT_FACEBOOK_PATH = "/connect/facebook";
  private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);
  @Autowired
  UserRepo userRepo;


  @Autowired
  UserService userService;

  @Autowired
  JwtService jwtService;

  private RestTemplate restTemplate;

  public AuthenticationController(RestTemplateBuilder restTemplateBuilder) {
    restTemplate = restTemplateBuilder.build();
  }

  /*
     This mapping is used in two cases:
     1- A new user is authenticating with facebook.
     2- An existing user is authenticating with facebook because their JWT token is expired or
        or they don't have it in their session.

     This request is expected to called by a non-logged in user so the security context
     authentication is expected to be not set.
  */
  @GetMapping(value = LOGIN_WITH_FACEBOOK_PATH)
  public ResponseEntity<FacebookAuthenticationResponse> loginWithFacebook(
      @RequestBody FacebookAuthenticationBody requestBody) {
    requestBody.validate();
    FacebookAuthenticationResponse response = new FacebookAuthenticationResponse();

    if (!(SecurityContextHolder.getContext()
        .getAuthentication() instanceof AnonymousAuthenticationToken)) {
      logger.error(
          "Did not expect a logged in user as this request will not pass through filters.");
      throw new RuntimeException();
    }

    FacebookBasicProfileResponse facebookResponse = assertUserFacebookData(requestBody);

    if (facebookResponse == null) {
      response
          .setError(new Error(FacebookAuthenticationResponse.AUTHENTICATION_WITH_FACEBOOK_ERROR));
      return ResponseEntity.unprocessableEntity().body(response);
    }

    String jwtToken;
    try {
      User user =
          userRepo.findByUserFacebookData_userId(facebookResponse.getId()) // Case 2
              .orElse(userService.buildNewUser(facebookResponse.email, // Case 1
                  facebookResponse.name));
      UserFacebookData userFacebookData = UserFacebookData.builder()
          .accessToken(requestBody.getToken())
          .userId(facebookResponse.id)
          .name(facebookResponse.name)
          .email(facebookResponse.email).build();
      user.setUserFacebookData(userFacebookData);
      userRepo.save(user);
      jwtToken = jwtService.generateToken(user);
    } catch (Exception e) {
      response
          .setError(new Error(FacebookAuthenticationResponse.AUTHENTICATION_WITH_FACEBOOK_ERROR));
      return ResponseEntity.unprocessableEntity().body(response);
    }

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setBearerAuth(jwtToken);
    ResponseEntity<FacebookAuthenticationResponse> responseEntity =
        new ResponseEntity<>(httpHeaders, HttpStatus.OK);
    return responseEntity;
  }


  /*
   This mapping should only be used in case of a logged in user who wants to connect their
   account with facebook. Note, that maybe they have already connected an account; in that case
   the new facebook information will override the old one.

     This request is expected to called by a logged in user so the security context
     authentication is expected to be set.
  */
  @GetMapping(value = CONNECT_FACEBOOK_PATH)
  public ResponseEntity<FacebookAuthenticationResponse> connectFacebook(
      @RequestBody FacebookAuthenticationBody requestBody) {
    requestBody.validate();
    FacebookAuthenticationResponse response = new FacebookAuthenticationResponse();

    FacebookBasicProfileResponse facebookResponse = assertUserFacebookData(requestBody);

    if (facebookResponse == null) {
      response
          .setError(new Error(FacebookAuthenticationResponse.AUTHENTICATION_WITH_FACEBOOK_ERROR));
      return ResponseEntity.unprocessableEntity().body(response);
    }

    User user =
        userRepo.findByUserFacebookData_userId(facebookResponse.getId())
            .orElse(userRepo.findById(getCurrentUser().getUserId()).get());
    if (!user.getId().equals(getCurrentUser().getUserId())) {
      logger.warn("Someone is trying to connect his facebook account to an account other than the"
          + " one which the facebook account is already connected to.");
      response
          .setError(new Error(FacebookAuthenticationResponse.SOMEONE_ELSE_ALREADY_CONNECTED_ERROR));
      return ResponseEntity.unprocessableEntity().body(response);
    }

    UserFacebookData userFacebookData = UserFacebookData.builder()
        .accessToken(requestBody.getToken())
        .userId(facebookResponse.id)
        .name(facebookResponse.name)
        .email(facebookResponse.email).build();
    user.setUserFacebookData(userFacebookData);
    userRepo.save(user);

    try {
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setBearerAuth(jwtService.generateToken(user));
      ResponseEntity<FacebookAuthenticationResponse> responseEntity =
          new ResponseEntity<>(httpHeaders, HttpStatus.OK);
      return responseEntity;
    } catch (UnsupportedEncodingException e) {
      response
          .setError(new Error(FacebookAuthenticationResponse.AUTHENTICATION_WITH_FACEBOOK_ERROR));
      return ResponseEntity.unprocessableEntity().body(response);
    }
  }

  private FacebookBasicProfileResponse assertUserFacebookData(FacebookAuthenticationBody body) {
    String facebook_graph_api_url =
        "https://graph.facebook.com/v7.0/me?fields=id,name,email&access_token=" + body.getToken();
    FacebookBasicProfileResponse facebookResponse = restTemplate.getForObject(
        facebook_graph_api_url,
        FacebookBasicProfileResponse.class);

    if (facebookResponse.id == null || !facebookResponse.id.equals(body.getUserId())) {
      return null;
    }

    return facebookResponse;

  }

  @Data
  public static class FacebookBasicProfileResponse {

    String id;
    String name;
    String email;
  }
}
