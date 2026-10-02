# Budget allocation layout — gateway correction

`Panel_Budget` is rendered as a mandatory middle gateway between the pre-budget and post-budget dialogue. The runtime mechanics are unchanged; this correction fixes how the exact source topology is laid out.

## Source topology verified from the bundled dialogue dump

For conversation `26`, the panel command is dialogue `209`:

`ShowPagedDecisionsPanel("Panel_Budget")`

The source facts used by the layout are:

- dialogue `26:209` has exactly one outgoing source link: `27:1`;
- `26:158` (`I turned the first page.`) is the only source entry that links directly to `26:209`;
- conversation 26 has exactly one cross-conversation progress exit, the `26:209 -> 27:1` panel transition;
- `27:1` has one outgoing source link to `27:81`;
- `27:81` is the single post-panel narrator line beginning `I had finished allocating the government budget...`.

The pre-budget conversation contains a large loop. Some loop-return edges are classified as drawing back-references to make the ordinary dialogue layout acyclic. A normal layered layout can therefore place legitimate Part A loop nodes below the panel even though they are still source-reachable back into the pre-budget loop. That was the cause of the apparent bypass lines.

## Required shape

The event is now laid out as three stacked regions:

1. **Part A** — all pre-budget dialogue and its loop/dead-end branches.
2. **Budget gateway** — one complete outer rectangle containing all four ministries, all choices, all immediate effect cards, and the merge node.
3. **Part B** — all dialogue reachable after the panel continuation.

The outer Budget rectangle contains, left-to-right:

`Health | Law Enforcement | Education | Military`

Each ministry branches downward into:

`Decrease | Maintain | Increase`

Each choice then leads to its immediate effect card. All twelve immediate effect cards visually reconverge into one visible **Budget chosen** node inside the rectangle.

The only external input reaches the **upper-middle boundary** of the rectangle. The only source continuation leaves from **Budget chosen**, passes through the **lower-middle boundary**, and enters Part B.

## Gateway partition

The layout partition deliberately uses full source reachability, including source back-reference edges, rather than only the acyclic drawing subset:

- anything that can source-reach the panel belongs to Part A;
- anything reachable from the panel completion belongs to Part B;
- detached/dead-end records from the panel's source conversation remain with Part A;
- Part A and Part B must not overlap;
- a direct source edge from Part A to Part B is rejected as a topology violation instead of being silently drawn around the panel.

This keeps the large pre-budget loop entirely above the gateway instead of allowing loop-return cards to fall below it.

## Geometry guarantees

- every Budget member is inside the one outer rectangle;
- no unrelated connector enters the rectangle;
- no external source edge spans from above the rectangle to below it except through the panel boundary ports;
- all source edges are retained exactly once;
- the source continuation remains `26:209 -> 27:1`;
- the runtime choice/effect data and raw source metadata remain unchanged.
