# Validation protocol

Use chronological train/test splits, never random splits across time. Keep an information cutoff for every historical prediction. Compare to simple baselines and de-vigged market probabilities. Report sample size, Brier score, log loss, calibration buckets and flat-stake ROI with uncertainty. Evaluate each market separately. Run feature ablations and monitor drift by competition, season and odds band. Do not claim an edge from small samples or in-sample fit.
