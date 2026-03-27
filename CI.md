# CI/CD

This project uses GitHub Actions and Dependabot to automate builds, dependency updates, and releases.

## Workflows

### CI (`ci.yml`)

Runs on every pull request targeting `main`. Builds the project with `mvn clean package` to validate that the code compiles and packages correctly.

### Build and Package (`build.yml`)

Runs when a tag is pushed. Builds the project using the tag as the version, uploads the JAR as an artifact, and creates a GitHub release with the JAR attached. Before building, syncs the `ServerVersion` in `manifest.json` from the Hytale Server dependency version in `pom.xml`.

Use this for manual releases:

```bash
git tag 1.2.0
git push origin 1.2.0
```

### Dependabot auto-merge (`dependabot-auto-merge.yml`)

Runs when Dependabot opens a pull request. Automatically approves the PR and enables auto-merge (squash). The merge happens once the CI check passes.

### Auto Release (`auto-release.yml`)

Runs when a Dependabot PR is merged into `main`. Builds the project and creates a GitHub release with the JAR. The version is generated as `YYYY.MM.DD-<short-sha>` (e.g. `2026.03.27-a1b2c3d`). Before building, syncs the `ServerVersion` in `manifest.json` from the Hytale Server dependency version in `pom.xml`.

## Dependabot (`dependabot.yml`)

Configured to check for Maven dependency updates weekly. All dependencies are grouped into a single PR. Custom registries are configured for `maven.hytale.com` (release and pre-release) and GitHub Packages (`g-portal/a2s-java`) so Dependabot can discover new versions of all dependencies.

## Automated Dependency Update Flow

```
Dependabot detects update
        |
        v
  Opens PR (all deps grouped)
        |
        +--> CI runs build ──── fails ──> PR stays open, manual intervention needed
        |
        +--> Auto-merge approves + enables auto-merge
        |
        v
   CI passes
        |
        v
  PR is auto-merged (squash)
        |
        v
  Auto Release builds + creates GitHub release
```

## Required Repository Settings

For the automated flow to work, two settings must be enabled:

1. **Allow auto-merge** — Settings > General > Pull Requests > check "Allow auto-merge"
2. **Branch protection on `main`** — Settings > Branches > Add rule for `main`:
   - Check "Require status checks to pass before merging"
   - Add `build` as a required status check (this is the job name from `ci.yml`)
