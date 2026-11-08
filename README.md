# Airplane Simulator

1. A new aircraft starts in `GROUNDED` state.
2. `prepareForTakeoff()` checks that the engine is operational, enough fuel is
   available, and a route is available. On success, the state becomes `READY`.
3. `takeOff()` starts the engine, retracts the landing gear, and changes the
   state to `IN_FLIGHT`.
4. `cruise(amount)` consumes fuel. If the engine is no longer operational, the
   aircraft makes an emergency landing. A fuel-consumption failure also causes
   an emergency landing and is reported as a `FlightException`.
5. `land()` and `emergencyLand()` deploy the landing gear, stop the engine,
   and change the state to `LANDED`.

An invalid lifecycle operation (for example, calling `takeOff()` before
preparation) throws `FlightException`.

- `FlightException` is an unchecked exception.
- `IllegalArgumentException` is used for invalid numeric input.
- `Engine.fail()` can simulate an engine failure during a flight.
- `NavigationSystem.setRouteAvailable(...)` can change route availability.
- Mockito and JUnit 5 dependencies are already configured with test scope in `pom.xml`.

## Tests

Interaction tests built with Mockito test doubles live in
`src/test/java/airplane/AircraftTest.java`. Every component of `Aircraft` is a
Mockito double; the tests stub component behaviour (`when/thenReturn`,
`thenThrow`, `doNothing/doThrow`) and verify how the aircraft talks to its
components (`verify`, `times`, `never`, `InOrder`, `reset`, argument matchers).

Run them with:

```bash
mvn test
```

See `REPORT.md` for the mapping from each required Mockito concept to the test
that uses it and the reasoning behind each test.
