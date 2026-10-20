package software.frisby.web.vthreads;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import software.frisby.web.server.sse.SseEmitter;
import software.frisby.web.server.sse.SseEvent;

/**
 * Minimal JAX-RS resource used only by this module's virtual-thread smoke tests.
 * <p>
 * Each endpoint reports whether the thread that executed the handler body is a
 * {@linkplain Thread#isVirtual() virtual thread} — the single fact every test in this
 * module exists to prove, for the request-handling dispatch thread ({@link #ping()}) and
 * the SSE event-producing thread ({@link #sse}) respectively.
 */
@Path("/virtual-threads")
public final class VirtualThreadsTestResource {
    /**
     * Reports whether the calling request-handling thread is virtual.
     *
     * @return {@code true} or {@code false}, JSON-serialized by the configured
     * {@code JsonSerializer}.
     */
    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Boolean ping() {
        return Thread.currentThread().isVirtual();
    }

    /**
     * Emits a single SSE event whose body reports whether the thread that produced it
     * (i.e. the thread executing this resource method) is virtual.
     *
     * @param sink Jersey's SSE sink for this connection.
     * @param sse  Jersey's SSE context, required to build {@link SseEmitter}.
     */
    @GET
    @Path("/sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void sse(@Context SseEventSink sink, @Context Sse sse) {
        boolean handlerThreadIsVirtual = Thread.currentThread().isVirtual();

        try (SseEmitter emitter = SseEmitter.builder().sink(sink).sse(sse).build()) {
            emitter.send(
                    SseEvent.builder()
                            .event("thread-info")
                            .data(Boolean.toString(handlerThreadIsVirtual))
                            .build()
            ).join();
        }
    }
}





