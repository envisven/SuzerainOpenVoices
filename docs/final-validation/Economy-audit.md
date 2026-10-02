# BaseGame.Economy source audit

| Metric | Count |
| --- | ---: |
| Indexed writes | 219 |
| Rendered independent blocks | 219 |
| Source routes (DAG segments, not enumerated playthroughs) | 9201 |
| Player-choice trigger cells | 81 |
| State-condition cells | 7286 |
| Event/story/option trigger cells | 131 |
| Partial source routes | 373 |
| Boolean tautology transformations | 1185 |
| Factoring transformations | 2 |
| Retained distinct source references | 9201 |

Transformations are counted distinctly per write. Partial routes retain their supported predicates and exact stopping boundaries; they are not complete reachability proofs. Source references include condition-free paths and are available in Source mode.
