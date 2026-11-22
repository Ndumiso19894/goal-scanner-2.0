# StatsBomb research sandbox — non-commercial only

**This directory is not part of the Goal Scanner production application.** It is an isolated research aid for a person conducting non-commercial football analytics research.

## Mandatory use boundary

The StatsBomb Open Data repository is governed by the StatsBomb Public Data User Agreement, not a standard open-data licence. The agreement restricts redistribution/reproduction/provision of the data to third parties and prohibits commercial exploitation of the data **and analysis derived from it**. Publications of analysis must credit StatsBomb and display the StatsBomb brand logo. Read the current agreement in the official source before every new project or publication:

- Official data: https://github.com/hudl/open-data
- Official user agreement: https://github.com/hudl/open-data/blob/master/LICENSE.pdf
- StatsBomb media pack: https://statsbomb.com/media-pack/

This sandbox therefore has these hard boundaries:

1. Use only for your own non-commercial research and learning, subject to the current agreement.
2. Do not use StatsBomb data, outputs, metrics, model weights, learned parameters, or conclusions in Goal Scanner's commercial/monetised product.
3. Do not upload, commit, mirror, cache in a shared service, publish, or send StatsBomb data or derived datasets/reports to another person or third party.
4. Do not put StatsBomb data or derived outputs in GitHub Actions, public artifacts, app builds, APIs, dashboards, or the Android application.
5. Do not modify the source data. The utility in this folder reads a local official checkout without writing to it.
6. If any analysis is published, comply with the agreement's StatsBomb attribution and logo requirements. Do not assume attribution alone grants commercial rights.
7. If the intended use changes, stop and obtain written permission/licensing from StatsBomb/Hudl before proceeding.

The code in this folder is only a local read-only inventory helper. It does not download data, train a model, save derived outputs, or integrate with production. A user must independently obtain the official public repository and run the helper locally.

## Local inventory

Clone the official repository to a private local folder, then run from this project root:

```bash
python research/statsbomb/inventory.py --data-root /path/to/open-data/data
```

On Windows, use a local path such as `C:\\Users\\you\\Documents\\statsbomb-open-data\\data`.

The helper prints aggregate inventory counts only. Keep the results private and non-commercial. Do not attach them to this repository or use them as Goal Scanner model inputs.

## Production separation

There must be no import from `research.statsbomb` in `goal_scanner/`, no provider registration, no production API route, and no Android dependency. Mr2Odds integrations remain unchanged.
