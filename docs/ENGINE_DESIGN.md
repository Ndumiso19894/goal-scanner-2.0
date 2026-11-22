# Engine design

Pipeline: fixture discovery -> timestamped evidence -> odds capture/de-vigging -> baseline probabilities -> quality gates -> immutable snapshots -> decision/NO BET -> chronological evaluation.

All features must be available at the prediction information cutoff. Historical tests fail if the cutoff follows kickoff. Injury, suspension, lineup, tactics, referee, travel, rest, weather and pitch details begin as evidence, not arbitrary probability adjustments. Admit features only after chronological out-of-sample validation, calibration and ablation.

Every source adapter must declare coverage, quota, attribution, update cadence and failure behaviour. Missing records never imply player availability.
