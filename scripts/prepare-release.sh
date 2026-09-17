#!/usr/bin/env bash

# Automates the release-preparation steps that would otherwise be performed
# manually:
#   1. Switch to develop and pull the latest changes.
#   2. Create feature/prepare-release-<version>.
#   3. Run Maven in Docker to update all project POM versions.
#   4. Open CHANGELOG.md for editing unless --no-edit-changelog is supplied.
#   5. Commit and push the preparation branch.
#   6. Open a pull request into develop.
#
# After the PR is reviewed and merged, build-and-deploy-to-aws.yml publishes
# the development image. Create release/<version> from develop with no further
# commits, then open its pull request into main to publish the formal release.

set -euo pipefail

usage() {
  echo "Usage: $0 <release-version> [--no-edit-changelog]"
  echo
  echo "Example: $0 0.90.4 --no-edit-changelog"
  echo 
  echo "Requirements: Docker, git, and the GitHub CLI (gh)."
  echo "Java and Maven do not need to be installed; Maven runs in Docker."
}

if [[ $# -lt 1 || $# -gt 2 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  usage >&2
  exit 1
fi

version="$1"
prepare_branch="feature/prepare-release-${version}"
edit_changelog=true

if [[ $# -eq 2 ]]; then
  if [[ "$2" != "--no-edit-changelog" ]]; then
    echo "Unknown option: $2" >&2
    usage >&2
    exit 1
  fi
  edit_changelog=false
fi

for command in git docker gh; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command not found: $command" >&2
    exit 1
  fi
done

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree is not clean. Commit or stash your changes first." >&2
  exit 1
fi

git switch develop
git pull --ff-only origin develop

if git show-ref --verify --quiet "refs/heads/${prepare_branch}" || \
   git ls-remote --exit-code --heads origin "${prepare_branch}" >/dev/null 2>&1; then
  echo "Branch already exists: ${prepare_branch}" >&2
  exit 1
fi

git switch -c "${prepare_branch}"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env HOME=/tmp \
  --env MAVEN_CONFIG=/tmp/.m2 \
  --volume "${PWD}:/workspace" \
  --workdir /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  mvn --batch-mode --no-transfer-progress \
    versions:set versions:use-dep-version \
    -DnewVersion="${version}" \
    -DprocessAllModules=true \
    -Dincludes=uk.gov.dbt.ndtp.secure-agent.graph:sag-system \
    -DdepVersion="${version}" \
    -DforceVersion=true \
    -DgenerateBackupPoms=false

if [[ "${edit_changelog}" == true ]]; then
  read -r -a editor_command <<< "${VISUAL:-${EDITOR:-vi}}"
  "${editor_command[@]}" CHANGELOG.md
fi

if git diff --quiet -- pom.xml sag-server/pom.xml sag-system/pom.xml sag-docker/pom.xml; then
  echo "The POM files are already at version ${version}; nothing to commit." >&2
  git switch develop
  git branch -d "${prepare_branch}"
  exit 1
fi

git add pom.xml sag-server/pom.xml sag-system/pom.xml sag-docker/pom.xml CHANGELOG.md
git commit -m "chore: prepare release ${version}"
git push --set-upstream origin "${prepare_branch}"

gh pr create \
  --base develop \
  --head "${prepare_branch}" \
  --title "chore: prepare release ${version}" \
  --body "Updates the Maven project version to ${version} before creating release/${version}."

echo "Preparation PR created. After it is reviewed and merged, create release/${version} from develop."
