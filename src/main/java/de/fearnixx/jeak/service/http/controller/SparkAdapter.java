package de.fearnixx.jeak.service.http.controller;

import de.fearnixx.jeak.Main;
import de.fearnixx.jeak.reflect.http.RequestMapping;
import de.fearnixx.jeak.reflect.http.RestController;
import de.fearnixx.jeak.reflect.http.params.PathParam;
import de.fearnixx.jeak.reflect.http.params.QueryParam;
import de.fearnixx.jeak.reflect.http.params.RequestBody;
import de.fearnixx.jeak.reflect.http.params.RequestContext;
import de.fearnixx.jeak.service.http.RequestMethod;
import de.fearnixx.jeak.service.http.ResponseEntity;
import de.fearnixx.jeak.service.http.connection.HttpServer;
import de.fearnixx.jeak.service.http.connection.RestConfiguration;
import de.fearnixx.jeak.service.http.request.IRequestContext;
import de.fearnixx.jeak.service.http.request.SparkRequestContext;
import de.fearnixx.jeak.service.http.request.auth.token.TokenAuthService;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.fearnixx.jeak.service.http.request.IRequestContext.Attributes;


public class SparkAdapter extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(SparkAdapter.class);
    public static final int NUM_THREADS = Main.getProperty("jeak.http.poolsize", -1);
    public static final int MAX_THREADS = Main.getProperty("jeak.sparkadapter.maxpoolsize", 8);
    public static final int MIN_THREADS = Main.getProperty("jeak.sparkadapter.minpoolsize", 3);
    public static final int TIMEOUT_MILLIS = 30000;

    private final TokenAuthService tokenAuthService;
    private Map<String, String> headers;
    private Service service;

    public SparkAdapter(RestConfiguration restConfiguration, TokenAuthService tokenAuthService) {
        super(restConfiguration);
        service = Service.ignite();
        // only use NUM_THREADS, if it was configured
        if (NUM_THREADS > 0) {
            service.threadPool(NUM_THREADS, NUM_THREADS, TIMEOUT_MILLIS);
        } else {
            service.threadPool(MAX_THREADS, MIN_THREADS, TIMEOUT_MILLIS);
        }
        this.tokenAuthService = tokenAuthService;
    }

    /**
     * Register a provided controller at the Server utilizing Sparks ability to build hierarchical endpoints.
     *
     * @param controllerContainer A {@link ControllerContainer}.
     */
    public void registerController(ControllerContainer controllerContainer) {
        controllerContainer.getControllerMethodList().forEach(controllerMethod -> {
            String path = buildEndpoint(controllerContainer, controllerMethod);
            registerMethod(controllerMethod.getRequestMethod(),
                    path,
                    generateRoute(path, controllerContainer, controllerMethod));
        });
    }

    /**
     * Registers a method to Spark.
     *
     * @param httpMethod The {@link RequestMethod} to map the method to.
     * @param path       The api path as {@link String}for the given method.
     * @param route      The {@link Route} which is supposed to be invoked when a call to the specified
     *                   {@code path} and {@code httpMethod} is made.
     */
    private void registerMethod(RequestMethod httpMethod, String path, Route route) {
        checkAndSetCors(path);
        switch (httpMethod) {
            case GET:
                service.get(path, route);
                break;
            case PUT:
                service.put(path, route);
                break;
            case POST:
                service.post(path, route);
                break;
            case PATCH:
                service.patch(path, route);
                break;
            case DELETE:
                service.delete(path, route);
                break;
            case HEAD:
                service.head(path, route);
                break;
            default:
                logger.warn("Failed to register the route for: {}", path);
                break;
        }
    }

    private void checkAndSetCors(String path) {
        if (isCorsEnabled()) {
            service.options(path, (request, response) -> {
                headers = setHeaders(response, new HashMap<>());
                return "";
            });
        }
    }

    /**
     * Generate a Spark specific {@link Route} for the provided controller and method.
     *
     * @param path                The path for the specified route
     * @param controllerContainer The {@link ControllerContainer} of the controller.
     * @param controllerMethod    The {@link ControllerMethod} of the method.
     * @return A {@link Route} containing the actions of the {@link ControllerMethod}.
     */
    private Route generateRoute(String path, ControllerContainer controllerContainer, ControllerMethod controllerMethod) {
        List<MethodParameter> methodParameterList = controllerMethod.getMethodParameters();
        addBeforeHandlingCheck(path, controllerContainer, controllerMethod);
        return (request, response) -> {
            Object[] methodParameters = extractParameters(methodParameterList, request);
            Object returnValue = null;
            try {
                returnValue = controllerContainer.invoke(controllerMethod, methodParameters);
            } catch (Exception e) {
                response.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
                logger.warn("There was an error while handling the request: " + e.getMessage(), e);
            }
            Map<String, String> additionalHeaders = new HashMap<>();
            if (returnValue instanceof ResponseEntity) {
                ResponseEntity responseEntity = (ResponseEntity) returnValue;
                if (responseEntity.getStatus() > 0) {
                    response.status(responseEntity.getStatus());
                }
                additionalHeaders.putAll(responseEntity.getHeaders());
                returnValue = responseEntity.getEntity();
            }
            headers = setHeaders(response, additionalHeaders);

            String contentType = headers.get("Content-Type");
            if (contentType == null || ("application/json".equals(contentType))) {
                response.type("application/json");
                returnValue = toJson(returnValue);
            }
            return returnValue;
        };
    }

    private Object[] extractParameters(List<MethodParameter> methodParameterList, Request request) {
        Object[] methodParameters;
        if (methodParameterList == null) {
            methodParameters = new Object[0];
        } else {
            methodParameters = new Object[methodParameterList.size()];
            for (MethodParameter methodParameter : methodParameterList) {
                Object retrievedParameter = null;
                if (methodParameter.hasAnnotation(PathParam.class)) {
                    retrievedParameter = transformRequestOption(request.params(getPathParamName(methodParameter)), request, methodParameter);
                } else if (methodParameter.hasAnnotation(QueryParam.class)) {
                    retrievedParameter = transformRequestOption(request.queryMap(getRequestParamName(methodParameter)).value(), request, methodParameter);
                } else if (methodParameter.hasAnnotation(RequestBody.class)) {
                    retrievedParameter = transformRequestOption(request.body(), request, methodParameter);
                } else if (methodParameter.hasAnnotation(RequestContext.class)) {
                    retrievedParameter = transformRequestContext(request, methodParameter);
                }
                methodParameters[methodParameter.getPosition()] = retrievedParameter;
            }
        }
        return methodParameters;
    }

    protected Object transformRequestContext(Request request, MethodParameter methodParameter) {
        RequestContext annotation = methodParameter.getAnnotation(RequestContext.class).orElseThrow();
        var attributeIdent = annotation.attribute();

        if (attributeIdent.isBlank()) {
            return request.attribute(IRequestContext.Attributes.REQUEST_CONTEXT);
        } else {
            var storedValue = request.attribute(attributeIdent);

            if (annotation.required() && storedValue == null) {
                switch (attributeIdent) {
                    case IRequestContext.Attributes.AUTHENTICATION_USER:
                        if (request.attribute(IRequestContext.Attributes.AUTHENTICATION_TOKEN) != null) {
                            // #halt throws RuntimeException!
                            service.halt(HttpStatus.FORBIDDEN_403, "Only users are allowed to use this endpoint.");
                        } else {
                            // #halt throws RuntimeException!
                            service.halt(HttpStatus.UNAUTHORIZED_401);
                        }
                        return null;
                    case IRequestContext.Attributes.AUTHENTICATION_TOKEN:
                        // #halt throws RuntimeException!
                        service.halt(HttpStatus.UNAUTHORIZED_401);
                        return null;
                    default:
                        var msg = String.format("Required context attribute \"%s\" is unset!", attributeIdent);
                        throw new IllegalStateException(msg);
                }
            } else if (storedValue == null) {
                return null;
            } else {
                if (!methodParameter.getType().isAssignableFrom(storedValue.getClass())) {
                    var msg = String.format("Context attribute is not compatible with parameter type: \"%s\" vs. \"%s\"", storedValue.getClass(), methodParameter.getType());
                    throw new IllegalStateException(msg);
                }
                return storedValue;
            }
        }
    }

    private void addBeforeHandlingCheck(String path, ControllerContainer controllerContainer, ControllerMethod controllerMethod) {
        service.before(path, (request, response) -> {
            controllerContainer.getAnnotation(RestController.class).ifPresent(restController -> {
                if (getRestConfiguration().rejectUnencryptedTraffic().orElse(RestConfiguration.DEFAULT_HTTPS_REJECT_UNENCRYPTED) && !isProtocolHttps(request)) {
                    logger.info("HTTPS enforcement enabled, non HTTPS request for {} blocked", path);
                    service.halt(426, "{\"errors\": [\"Use of HTTPS is mandatory for this endpoint\"]}");
                }
            });

            if (!tokenAuthService.attemptAuthentication(request)) {
                var mapping = controllerMethod.getAnnotation(RequestMapping.class);
                if (mapping.isPresent()) {
                    logger.info("Unauthenticated HTTP request blocked.");
                    service.halt(HttpStatus.UNAUTHORIZED_401, "{\"errors\": [\"Authentication is mandatory for this endpoint!\"]");
                }
            }

            request.attribute(Attributes.REQUEST_CONTEXT, new SparkRequestContext(request));
        });
    }

    private boolean isProtocolHttps(Request request) {
        return request.protocol().contains(HTTPS_PROTOCOL) ||
                (getRestConfiguration().isBehindSslProxy().orElse(false)
                        && request.headers(X_FORWARDED_PROTO_HEADER) != null &&
                        !request.headers(X_FORWARDED_PROTO_HEADER).isBlank() &&
                        request.headers(X_FORWARDED_PROTO_HEADER).contains(HTTPS_PROTOCOL));
    }

    @Override
    public void start() {
        getRestConfiguration().getPort().ifPresent(service::port);
        if (getRestConfiguration().isHttpsEnabled()) {
            logger.info("HTTPS enabled");
            initHttps();
        } else {
            logger.info("HTTPS disabled");
        }
    }

    private void initHttps() {
        Optional<String> httpsKeystorePath = getRestConfiguration().getHttpsKeystorePath();
        Optional<String> httpsKeystorePassword = getRestConfiguration().getHttpsKeystorePassword();
        Optional<String> httpsTruststorePath = getRestConfiguration().getHttpsTruststorePath();
        Optional<String> httpsTruststorePassword = getRestConfiguration().getHttpsTruststorePassword();

        if (httpsKeystorePath.isPresent() && httpsKeystorePassword.isPresent()) {
            service.secure(httpsKeystorePath.get(), httpsKeystorePassword.get(), null, null);
            if (httpsTruststorePath.isPresent() && httpsTruststorePassword.isPresent()) {
                service.secure(httpsKeystorePath.get(), httpsKeystorePassword.get(), httpsTruststorePath.get(), httpsTruststorePassword.get());
            }
        }
    }

    /**
     * This method adds the provided {@link Map} of headers to the provided {@link Response}.
     *
     * @param response
     * @param additionalHeaders Some additional headers specified by the developer. Can override the configured default
     *                          headers - also the CORS headers.
     * @return A combination of all headers.
     */
    private Map<String, String> setHeaders(Response response, Map<String, String> additionalHeaders) {
        Map<String, String> headerMap = loadHeaders();
        if (isCorsEnabled()) {
            headerMap.putAll(loadCorsHeaders());
        }
        // Important to add the additional headerMap afterwards, so they can override the others
        headerMap.putAll(additionalHeaders);
        headerMap.forEach(response::header);
        return headerMap;
    }
}
