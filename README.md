# Cart-Pole Swing-Up: Hand-Tuned Controller + Neural Net Imitation Attempt

A cart-pole (inverted pendulum on a cart) simulator built from scratch in Java —
custom RK4 physics integration, a hand-tuned hybrid controller (LQR + energy-style
swing-up state machine), and a from-scratch feedforward neural network (no ML
libraries — manual forward pass and backpropagation) trained to imitate the
controller.

## What's here

- `sipnnsim.java` — physics simulation, the expert controller, network training loop
- `sipnnsim1.java` — Swing GUI viewer to watch the controller run live

## The physics

Standard cart-pole dynamics, integrated with 4th-order Runge-Kutta at `dt = 0.001`.
Cart mass, pole mass, and pole length are all configurable; the pole angle `theta`
is measured so that `theta = 0` is hanging straight down and `theta = π` is
balanced upright.

## The controller (the part that works)

A hybrid controller in `fuckyou()`:

- **Near upright** (`|error| < ~23°`): a hand-derived LQR controller (gains
  `K1..K4`) linearized around the upright equilibrium.
- **Approaching upright** (`|error| < 45°`): a simpler "catch" law using just
  the angle and angular velocity terms.
- **Far from upright**: a state machine (`drive` → `hook` → `relief` → repeat)
  that drives the cart back and forth to pump energy into the pole until it's
  close enough for the catch controller to take over.

This gets the pole upright from a hanging start roughly **80% of the time**.
The other ~20% are cases where the pump cycle runs out of track (the cart hits
the rail limit) or mistimes the catch window.

## The neural network attempt (the part that didn't)

A `{5, 16, 8, 1}` feedforward network (leaky-ReLU hidden layers, sigmoid
output) was trained via imitation learning — recording `(state, expert force)`
pairs while the hand-tuned controller ran, then training the network to
reproduce the same mapping.

**Result: it did not reliably reproduce the expert's behavior**, despite
training MSE getting quite low in places.

### Why (the actual finding)

The expert controller's swing-up decisions depend on *internal state* —
which phase of `drive`/`hook`/`relief` it's currently in, and the `target`
position that phase is chasing. That state isn't part of the network's
input. The network only sees `(x, ẋ, θ, θ̇)`.

The consequence: the **same physical state** can appear in the training data
labeled with genuinely different "correct" forces, depending on which phase
the expert happened to be in when that sample was recorded. A network with
only the physical state as input can't tell those cases apart — it has no
way to resolve the contradiction except by fitting noise. In several runs,
*lower* training MSE actually corresponded to *worse* live behavior, because
the network was fitting harder to an inherently ambiguous mapping rather than
learning anything about the swing-up dynamics themselves.

This is a specific instance of a known imitation-learning failure mode
(covariate shift / non-Markovian expert labels), not a tuning problem — more
data, more training passes, or a bigger network wouldn't fix it, because the
input the network is given genuinely doesn't contain enough information to
predict the expert's output.

A version of the network trained **only on the smooth LQR/catch region**
(where the expert has no hidden state and every input maps to exactly one
correct output) worked far better, which supports this diagnosis — it's not
that the network can't learn control at all, it's specifically the
state-machine region that breaks it.

## Running it

Requires a JDK. From the project directory:

```bash
javac sipnnsim.java
java sipnnsim
```

This will train the network from scratch and print live cart/pole state as
`weight.txt` / `bias.txt` train. To just watch the hand-tuned controller run
(no network involved):

```bash
javac sipnnsim1.java
java sipnnsim1
```

## Takeaways

- The controller is the actual engineering result here — hand-derived LQR
  gains plus a tuned energy-pump state machine, ~80% success rate.
- The network is an honest negative result with a diagnosed cause, not just
  "didn't work." The failure is specifically about giving a stateless model
  a non-Markovian target to imitate.
