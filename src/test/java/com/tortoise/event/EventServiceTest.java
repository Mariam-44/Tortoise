package com.tortoise.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.tortoise.common.error.ApiException;
import com.tortoise.common.error.ErrorCode;
import com.tortoise.event.dto.CreateEventRequest;
import com.tortoise.event.dto.EventResponse;
import com.tortoise.event.dto.UpdateEventRequest;

/**
 * Plain unit tests: no Spring, no HTTP. We build the service by hand with a fixed clock,
 * so "now" is always 2026-12-01 and the 2027 dates below are always in the future.
 */
class EventServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);
    private static final int THREADS = 50;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(new InMemoryEventRepository(), FIXED_CLOCK);
    }

    @RepeatedTest(10)
    void parallelCreatesOfTheSameEventLetExactlyOneWin() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                startGun.await();                 // every thread waits here...
                try {
                    service.create(derby());
                    return true;                  // 201
                } catch (ApiException ex) {
                    assertEquals(ErrorCode.DUPLICATE_EVENT, ex.code());
                    return false;                 // 409
                }
            }));
        }
        startGun.countDown();                     // ...until all are released at once

        int winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertEquals(1, winners, "exactly one create should win");
        assertEquals(1, service.list(null, null, null, 0, 100).totalElements());
    }

    @Test
    void renamingAnEventIntoAnExistingOneIsRejected() {
        service.create(derby());
        EventResponse concert = service.create(event("Concert", "2027-01-10T19:00:00Z"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.update(concert.id(), patchName("al ahly vs zamalek")));
        assertEquals(ErrorCode.DUPLICATE_EVENT, ex.code());
    }

    @Test
    void movingAnEventOntoAnotherEventsDayIsRejected() {
        service.create(derby());
        EventResponse sameNameNextDay = service.create(event("Al Ahly vs Zamalek", "2027-01-11T19:00:00Z"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.update(sameNameNextDay.id(), patchStartsAt("2027-01-10T19:00:00Z", "2027-01-10T21:00:00Z")));
        assertEquals(ErrorCode.DUPLICATE_EVENT, ex.code());
    }

    @Test
    void editingOnlySeatsDoesNotCountTheEventAsItsOwnDuplicate() {
        EventResponse derby = service.create(derby());

        EventResponse updated = service.update(derby.id(), patchSeats(900));

        assertEquals(900, updated.totalSeats());
    }

    @Test
    void aCancelledEventFreesItsName() {
        EventResponse first = service.create(derby());
        service.cancel(first.id());

        EventResponse second = service.create(derby());

        assertEquals(EventStatus.DRAFT, second.status());
    }

    private static CreateEventRequest derby() {
        return event("Al Ahly vs Zamalek", "2027-01-10T19:00:00Z");
    }

    private static CreateEventRequest event(String name, String startsAt) {
        Instant starts = Instant.parse(startsAt);
        return new CreateEventRequest(name, null, "Cairo International Stadium", 500,
                Instant.parse("2027-01-01T10:00:00Z"), Instant.parse("2027-01-02T10:00:00Z"),
                starts, starts.plusSeconds(2 * 3600));
    }

    private static UpdateEventRequest patchName(String name) {
        return new UpdateEventRequest(name, null, null, null, null, null, null, null);
    }

    private static UpdateEventRequest patchSeats(int seats) {
        return new UpdateEventRequest(null, null, null, seats, null, null, null, null);
    }

    private static UpdateEventRequest patchStartsAt(String startsAt, String endsAt) {
        return new UpdateEventRequest(null, null, null, null, null, null,
                Instant.parse(startsAt), Instant.parse(endsAt));
    }
}
