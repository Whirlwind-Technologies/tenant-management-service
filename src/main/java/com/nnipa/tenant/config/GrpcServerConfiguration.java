package com.nnipa.tenant.config;

import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ServerCall.Listener;

/**
 * gRPC Server Configuration for Tenant Management Service
 */
@Slf4j
@Configuration
public class GrpcServerConfiguration {

    /**
     * Global gRPC interceptor for logging and monitoring
     */
    @GrpcGlobalServerInterceptor
    public ServerInterceptor loggingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                String methodName = call.getMethodDescriptor().getFullMethodName();
                String correlationId = extractCorrelationId(headers);
                long startTime = System.currentTimeMillis();

                log.debug("gRPC call started: {} [correlationId: {}]", methodName, correlationId);

                ServerCall<ReqT, RespT> wrappedCall = new io.grpc.ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long duration = System.currentTimeMillis() - startTime;

                        if (status.isOk()) {
                            log.info("gRPC call completed: {} [{}ms] [correlationId: {}]",
                                    methodName, duration, correlationId);
                        } else {
                            log.error("gRPC call failed: {} [{}ms] [correlationId: {}] - {}",
                                    methodName, duration, correlationId, status.getDescription());
                        }

                        super.close(status, trailers);
                    }
                };

                return next.startCall(wrappedCall, headers);
            }

            private String extractCorrelationId(Metadata headers) {
                Metadata.Key<String> correlationKey =
                        Metadata.Key.of("correlation-id", Metadata.ASCII_STRING_MARSHALLER);
                return headers.get(correlationKey) != null ?
                        headers.get(correlationKey) : "unknown";
            }
        };
    }

    /**
     * Exception handling interceptor
     */
    @GrpcGlobalServerInterceptor
    public ServerInterceptor exceptionHandlingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

                return new SimpleForwardingServerCallListener<ReqT>(listener) {
                    @Override
                    public void onHalfClose() {
                        try {
                            super.onHalfClose();
                        } catch (Exception e) {
                            log.error("Unhandled exception in gRPC call", e);
                            call.close(Status.INTERNAL
                                            .withDescription("Internal server error: " + e.getMessage()),
                                    new Metadata());
                        }
                    }
                };
            }
        };
    }
}