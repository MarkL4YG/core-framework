package de.fearnixx.t3.event;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.fearnixx.t3.Main;
import de.fearnixx.t3.event.bot.IBotStateEvent;
import de.fearnixx.t3.reflect.Inject;
import de.fearnixx.t3.reflect.Listener;
import de.fearnixx.t3.service.event.IEventService;
import de.mlessmann.logging.ILogReceiver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * EventService used by the T3ServerBot
 *
 * The event manager manages all events fired within an instance of the T3ServerBot.
 * Each bot creates it's own instances.
 *
 * The following system properties are acknowledged by the EventService class
 * * "bot.eventmgr.poolsize" (Integer)
 * * "bot.eventmgr.terminatedelay" (Integer in milliseconds)
 */
public class EventService implements IEventService {

    public static final Integer THREAD_POOL_SIZE = 10;
    public static Integer AWAIT_TERMINATION_DELAY = 5000;

    private ILogReceiver log;
    private final Object LOCK = new Object();
    private final List<EventListenerContainer> containers;

    private ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("event-scheduler-%d").build();
    private final ExecutorService eventExecutor;

    private boolean terminated;

    public EventService(ILogReceiver log) {
        this.log = log;
        containers = new LinkedList<>();
        terminated = false;

        eventExecutor = Executors.newFixedThreadPool(Main.getProperty("bot.eventmgr.poolsize", THREAD_POOL_SIZE), threadFactory);
        AWAIT_TERMINATION_DELAY = Main.getProperty("bot.eventmgr.terminatedelay", AWAIT_TERMINATION_DELAY);
    }

    /**
     * Fires a specified event to all suitable listeners
     * @param event {@link IEvent} instance
     */
    @Override
    public void fireEvent(IEvent event) {
        Objects.requireNonNull(event, "Event may not be null!");

        // Run on a temporary copy so adding new listeners during an event doesn't cause a dead-lock!
        final List<EventListenerContainer> listeners2 = new LinkedList<>();
        synchronized (LOCK) {
            if (terminated) return;
            for (EventListenerContainer container : containers)
                if (container.accepts(event.getClass()))
                    listeners2.add(container);
        }

        // Sort by event order
        listeners2.sort(Comparator.comparingInt(EventListenerContainer::getOrder));
        sendEvent(event, listeners2);
    }

    protected void sendEvent(IEvent event, List<EventListenerContainer> listeners) {
        log.finest("Sending event: ", event.getClass().getSimpleName(), " to ", listeners.size(), " listeners");

        Runnable runnable = () -> {
            for (int i = 0; i < listeners.size(); i++) {
                // Catch exceptions for each listener so a broken one doesn't break the whole event
                try {
                    // Check if we got interrupted during processing
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Interrupted during event execution");
                    listeners.get(i).accept(event);

                } catch (InterruptedException e) {
                    log.warning("Interrupted event: ", event.getClass().getSimpleName(), " ! Processed ", i + 1, " out of ", listeners.size(), e);
                    Thread.currentThread().interrupt();
                    return;

                } catch (EventAbortException abort) {
                    // Event aborted!
                    log.severe("An event has been aborted!", abort);
                    return;

                } catch (Exception e) {
                    // Skip the invocation exception for readability
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.severe("Failed to pass event ", event.getClass().getSimpleName(), " to ", listeners.get(i).getVictim().getClass().toGenericString(), cause);
                }
            }
        };

        // The following events shall not be called asynchronously:
        // * Initialize
        // * PostShutdown
        if (event instanceof IBotStateEvent.IPostShutdown
            || event instanceof IBotStateEvent.IInitializeEvent) {
            runnable.run();
        } else {
            // Execute using ThreadPoolExecutor
            eventExecutor.execute(runnable);
        }
    }

    /**
     * Adds a new {@link EventListenerContainer} to the event listeners
     */
    public void addContainer(EventListenerContainer container) {
        synchronized (LOCK) {
            containers.add(container);
        }
    }

    /**
     * @see IEventService#registerListeners(Object...)
     */
    @Override
    public void registerListeners(Object... l) {
        for (Object o : l)
            registerListener(o);
    }

    /**
     * @see IEventService#registerListener(Object)
     */
    @Override
    public void registerListener(Object victim) {
        Objects.requireNonNull(victim, "Listener victim may not be null!");

        synchronized (LOCK) {
            for (Method method : victim.getClass().getMethods()) {
                Listener anno = method.getAnnotation(Listener.class);
                if (anno == null)
                    continue;

                containers.add(new EventListenerContainer(victim, method));
            }
        }
    }

    /**
     * Attempts to shut down the EventService and all running event tasks.
     * Waits for termination as long as specified by AWAIT_TERMINATION_DELAY
     */
    public void shutdown() {
        synchronized (LOCK) {
            terminated = true;
            boolean terminated_successfully = false;
            try {
                eventExecutor.shutdownNow();
                terminated_successfully = eventExecutor.awaitTermination(AWAIT_TERMINATION_DELAY, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.severe("Got interrupted while awaiting thread termination!", e);
                Thread.currentThread().interrupt();
            }
            if (!terminated_successfully) {
                log.warning("Some events did not terminate gracefully! Either consider increasing the wait timeout or debug what plugin delays the shutdown!");
                log.warning("Be aware that the JVM will not exit until ALL threads have terminated!");
            }
        }
    }
}
