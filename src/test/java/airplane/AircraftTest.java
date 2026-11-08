package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link Aircraft} written with Mockito test doubles.
 *
 * <p>Every collaborator of {@code Aircraft} (engine, fuel system, landing gear,
 * navigation system) is replaced by a Mockito double. The same object plays two
 * roles depending on how a test uses it:
 * <ul>
 *   <li><b>Stub</b> – we program its answers up front ({@code when(..).thenReturn(..)},
 *       {@code thenThrow}, {@code doNothing}, {@code doThrow}) to put the aircraft
 *       into the situation we want to test.</li>
 *   <li><b>Mock</b> – after exercising the aircraft we ask it what happened to it
 *       ({@code verify(..)}, {@code times(..)}, {@code never()}, {@code inOrder}).</li>
 * </ul>
 *
 * <p>Naming convention: {@code methodName_stateUnderTest_expectedBehavior}.
 */
class AircraftTest {

    private static final double CRUISE_FUEL = 120.5;

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    /**
     * Fresh doubles for every test: {@code mock(Class)} hands back an object that
     * has the exact type of the real component but no behaviour at all. Unstubbed
     * methods return defaults ({@code false}, {@code 0}, {@code null}) and record
     * every call so that it can be verified later.
     */
    @BeforeEach
    void createDoubles() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    // ------------------------------------------------------------------ helpers

    /** Stubs the three pre-flight checks so that {@code prepareForTakeoff()} succeeds. */
    private void stubSuccessfulPreflightChecks() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    /** Drives the aircraft from GROUNDED to IN_FLIGHT using the stubbed happy path. */
    private void putAircraftInFlight() {
        stubSuccessfulPreflightChecks();
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus(), "precondition");
    }

    // ------------------------------------------------------- prepareForTakeoff()

    /**
     * Concepts: when() / thenReturn() (stub), verify() / times() (mock).
     * The aircraft must consult each of the three components exactly once and
     * must not touch the engine or the gear during preparation.
     */
    @Test
    void prepareForTakeoff_allChecksPass_consultsEachComponentOnceAndBecomesReady() {
        stubSuccessfulPreflightChecks();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    /**
     * Concepts: thenReturn(false) (stub), never() (mock).
     * The engine check is the first one; when it fails, the aircraft must stop
     * immediately and not ask the remaining components anything.
     */
    @Test
    void prepareForTakeoff_engineNotOperational_throwsAndSkipsRemainingChecks() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception =
                assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: engine is not operational.",
                exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, never()).hasEnoughFuel();
        verifyNoInteractions(navigationSystem, landingGear);
    }

    /**
     * Concept: thenReturn with mixed answers.
     * Engine and fuel are fine, only the route is missing, so the failure must
     * be reported as a navigation problem and the status must stay GROUNDED.
     */
    @Test
    void prepareForTakeoff_routeUnavailable_throwsNavigationFailure() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException exception =
                assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals("Cannot prepare for takeoff: route is unavailable.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    /**
     * Concept: thenThrow() (stub a non-void method to fail).
     * A faulty fuel gauge throws instead of answering. The aircraft does not
     * wrap such a fault, so it must propagate unchanged, the route must never be
     * consulted and the aircraft must remain GROUNDED.
     */
    @Test
    void prepareForTakeoff_fuelGaugeThrows_propagatesFaultAndStaysGrounded() {
        IllegalStateException gaugeFault = new IllegalStateException("fuel gauge offline");
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenThrow(gaugeFault);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, aircraft::prepareForTakeoff);

        assertSame(gaugeFault, thrown);
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, never()).isRouteAvailable();
    }

    // ---------------------------------------------------------------- takeOff()

    /**
     * Concepts: doNothing() (stub void methods explicitly), verify() + InOrder (mock).
     * {@code doNothing()} is already the default for a void method on a mock; it is
     * written out here to document that the engine and the gear are expected to
     * cooperate silently. The verification then proves the aircraft really started
     * the engine before retracting the gear, each exactly once.
     */
    @Test
    void takeOff_whenReady_startsEngineThenRetractsGearAndIsInFlight() {
        stubSuccessfulPreflightChecks();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        aircraft.prepareForTakeoff();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        InOrder takeoffSequence = inOrder(engine, landingGear);
        takeoffSequence.verify(engine, times(1)).start();
        takeoffSequence.verify(landingGear, times(1)).retract();
        verify(engine, never()).stop();
        verify(landingGear, never()).deploy();
    }

    /**
     * Concepts: doThrow() (stub a void method to fail), never() (mock).
     * {@code when(engine.start()).thenThrow(..)} would not compile because
     * {@code start()} is void, so the {@code doThrow().when(mock).method()} form
     * is required. The aircraft must wrap the cause, abort and keep the gear down.
     */
    @Test
    void takeOff_engineStartFails_abortsWrapsCauseAndKeepsGearDown() {
        IllegalStateException engineFault = new IllegalStateException("ignition failure");
        stubSuccessfulPreflightChecks();
        doThrow(engineFault).when(engine).start();
        aircraft.prepareForTakeoff();

        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Takeoff failed.", exception.getMessage());
        assertSame(engineFault, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(landingGear, never()).retract();
    }

    /**
     * Concept: verifyNoInteractions() (mock).
     * A lifecycle violation is detected by the aircraft itself, so no component
     * may be touched at all.
     */
    @Test
    void takeOff_beforePreparation_throwsWithoutTouchingAnyComponent() {
        FlightException exception = assertThrows(FlightException.class, aircraft::takeOff);

        assertEquals("Operation is not allowed while aircraft status is GROUNDED.",
                exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    // ----------------------------------------------------------------- cruise()

    /**
     * Concepts: verify() with an exact argument, any(Class) / anyDouble() matchers,
     * times() (mock). {@code any()} without a class would return {@code null} and
     * fail with a NullPointerException when unboxed into the primitive
     * {@code double} parameter, so the typed matcher is used instead.
     */
    @Test
    void cruise_whenInFlight_forwardsExactFuelAmountToFuelSystem() {
        putAircraftInFlight();

        aircraft.cruise(CRUISE_FUEL);
        aircraft.cruise(CRUISE_FUEL);
        aircraft.cruise(CRUISE_FUEL / 2);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(2)).consumeFuel(CRUISE_FUEL);
        verify(fuelSystem, times(1)).consumeFuel(CRUISE_FUEL / 2);
        verify(fuelSystem, times(3)).consumeFuel(any(Double.class));
        verify(fuelSystem, times(3)).consumeFuel(anyDouble());
        verify(landingGear, never()).deploy();
    }

    /**
     * Concepts: reset() (wipe stubs and recorded calls), thenReturn(false),
     * verify()/never()/anyDouble().
     *
     * <p>Getting the aircraft airborne needs {@code isOperational() == true} and
     * records {@code start()} on the engine. {@code reset(engine)} throws both
     * the stub and that history away, so the verifications below talk only about
     * the cruise phase: without the reset, {@code verify(engine, never()).start()}
     * would fail because of the takeoff.
     */
    @Test
    void cruise_engineFailsMidFlight_emergencyLandsWithoutConsumingFuel() {
        putAircraftInFlight();
        reset(engine);
        when(engine.isOperational()).thenReturn(false);

        aircraft.cruise(CRUISE_FUEL);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(engine, times(1)).stop();
        verify(engine, never()).start();
        verify(landingGear, times(1)).deploy();
        verify(fuelSystem, never()).consumeFuel(anyDouble());
    }

    /**
     * Concepts: doThrow() on a void method with an anyDouble() matcher (stub),
     * verify()/times() (mock). A fuel problem must trigger an emergency landing
     * and then be reported as a FlightException that carries the original cause.
     */
    @Test
    void cruise_fuelConsumptionFails_emergencyLandsAndReportsFlightException() {
        IllegalStateException fuelFault = new IllegalStateException("Not enough fuel.");
        putAircraftInFlight();
        doThrow(fuelFault).when(fuelSystem).consumeFuel(anyDouble());

        FlightException exception =
                assertThrows(FlightException.class, () -> aircraft.cruise(CRUISE_FUEL));

        assertEquals("A problem occurred while consuming fuel.", exception.getMessage());
        assertSame(fuelFault, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(CRUISE_FUEL);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    /**
     * Concept: verifyNoMoreInteractions() (mock).
     * Input validation happens inside the aircraft, before any component is
     * asked, so apart from the takeoff interactions nothing else may happen.
     */
    @ParameterizedTest(name = "cruise({0}) is rejected")
    @ValueSource(doubles = {0.0, -1.0, -CRUISE_FUEL})
    void cruise_nonPositiveAmount_throwsIllegalArgumentWithoutTouchingComponents(double amount) {
        putAircraftInFlight();
        verify(engine).isOperational();
        verify(engine).start();
        verify(fuelSystem).hasEnoughFuel();
        verify(landingGear).retract();
        verify(navigationSystem).isRouteAvailable();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(amount));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verifyNoMoreInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    /** Concept: verifyNoInteractions(). Cruising on the ground is a lifecycle error. */
    @Test
    void cruise_whenGrounded_throwsWithoutTouchingAnyComponent() {
        assertThrows(FlightException.class, () -> aircraft.cruise(CRUISE_FUEL));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    // ------------------------------------------------------- land() / lifecycle

    /**
     * Concepts: InOrder verification, times() (mock).
     * Landing must deploy the gear before the engine is stopped.
     */
    @Test
    void land_whenInFlight_deploysGearThenStopsEngine() {
        putAircraftInFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        InOrder landingSequence = inOrder(landingGear, engine);
        landingSequence.verify(landingGear, times(1)).deploy();
        landingSequence.verify(engine, times(1)).stop();
        verify(fuelSystem, never()).consumeFuel(anyDouble());
    }

    /** Concept: verifyNoInteractions(). Landing on the ground is a lifecycle error. */
    @Test
    void land_whenGrounded_throwsWithoutTouchingAnyComponent() {
        assertThrows(FlightException.class, aircraft::land);

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }

    /**
     * Concepts: stubs reused across a whole lifecycle, times() counting
     * cumulative calls. After landing, the aircraft may be prepared again and
     * the pre-flight checks must be repeated rather than remembered.
     */
    @Test
    void prepareForTakeoff_afterLanding_runsPreflightChecksAgain() {
        putAircraftInFlight();
        aircraft.land();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, times(2)).isOperational();
        verify(fuelSystem, times(2)).hasEnoughFuel();
        verify(navigationSystem, times(2)).isRouteAvailable();
        verify(engine, times(1)).start();
        verify(engine, times(1)).stop();
    }

    /** Guard clause: the aircraft refuses to be built without its components. */
    @Test
    void constructor_nullComponent_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Aircraft(null, fuelSystem, landingGear, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, null, landingGear, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, fuelSystem, null, navigationSystem));
        assertThrows(NullPointerException.class,
                () -> new Aircraft(engine, fuelSystem, landingGear, null));
    }
}
