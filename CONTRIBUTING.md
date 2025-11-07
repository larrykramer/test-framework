# Contributing to the Test Framework

## 1. Introduction

Thank you for considering contributing to the Test Framework!
I welcome bug reports, feature requests, documentation improvements, and code changes.

This document explains how to contribute.
Please review it before submitting your contribution.

## 2. How Can I Contribute?

There are many ways to contribute:

* **Reporting Bugs:**
  If you find a bug, please open an issue in the GitHub issue tracker.
* **Suggesting Enhancements:**
  Have an idea for a new feature or an improvement to an existing one?
  Open an issue to discuss it.
* **Submitting Pull Requests:**
  Contribute code or documentation changes directly to the project.
* **Documentation:**
  Improve existing documentation or add new guides or examples.
* **Answering Questions:**
  Help others by answering questions in the [Discussions](https://github.com/larrykramer/test-framework/discussions) section.

## 3. Getting Started

1.  **Fork the Repository:**
    Click the "Fork" button on the top right of the [Test Framework GitHub page](https://github.com/larrykramer/test-framework).
    This creates your own copy of the project.
2.  **Clone Your Fork:**
    ```shell
    # Replace <your-username> with your GitHub username
    git clone https://github.com/<your-username>/test-framework.git
    cd test-framework
    ```
3.  **Set Upstream Remote:**
    ```shell
    git remote add upstream https://github.com/larrykramer/test-framework.git
    ```
4.  **Keep Your Fork Synced:**
    Before creating a new branch, ensure your `main` branch is up-to-date with the upstream repository:
    ```shell
    git checkout main
    git pull upstream main
    ```
5.  **Create a New Branch:**
    Before making any changes, create a new branch for your work:
    ```shell
    git checkout -b feature/my-new-feature  # For new features
    # or
    git checkout -b fix/bug-description     # For bug fixes
    ```
    Please use a descriptive branch name.
6.  **Set Up Development Environment:**
    * Ensure you have JDK 21 or higher and Apache Maven 3.6.3 or higher installed.
    * Build the project to ensure everything is set up correctly:
      ```shell
      mvn clean verify
      ```

## 4. Coding Conventions

To keep the codebase consistent and readable, all contributions must adhere to my personal Java coding conventions.

My personal Java coding conventions are documented in the [Code Conventions](https://github.com/larrykramer/code-conventions) repository.
That repository is the single source of truth and provides the full conventions document, rationale, and master IntelliJ IDEA formatter file.

### 4.1 Applying the Standard

For your convenience, this project's repository already includes the necessary **IntelliJ IDEA formatter file** in the `.idea/codeStyles/` directory.
Your IDE should automatically pick up this project-level setting.

Please format your code (`Ctrl + Alt + L` on Windows/Linux, `⌥ + ⌘ + L` on macOS) before committing to ensure it aligns with the project's style.

### 4.2 Key Style Guidelines

For convenience, key aspects enforced by the formatter are summarized below:

* **File Structure and Imports:**
  Source files must follow a strict order: MIT license header, package declaration, import statements, and then the class declaration.
  Imports are grouped (module, `java.*`/`javax.*`, third-party, static) and sorted alphabetically.
* **Formatting:**
  Use a 100-character line limit and 4-space indentation (never tabs). Ensure proper whitespace around operators and blank lines between logical code blocks and methods.
* **Naming Conventions:**
  Follow standard Java naming practices: `UpperCamelCase` for classes, interfaces, and enums; `lowerCamelCase` for methods and variables; and `UPPER_SNAKE_CASE` for constants.
* **Declarations and Statements:**
  Declare variables close to their first use. Always use braces `{}` for the bodies of control structures (`if`, `for`, `while`, etc.), even for single-line blocks.
* **Modern Java Features:**
  Leverage modern language features appropriately. Prefer method references over simple lambdas, use text blocks for multi-line strings, and utilize pattern matching for `instanceof` checks and `switch` statements to reduce boilerplate.

## 5. Commit Message Guidelines

I have very precise rules for how Git commit messages must be formatted.
This format leads to a **more readable commit history** and makes it analyzable for changelog generation.

Each commit message consists of a **header**, a **body**, and a **footer**.

```text
<header>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

The `header` is mandatory and must conform to the [Commit Message Header](#51-commit-message-header) format.

The `body` is mandatory for all commits except those of type "docs".
When the body is present, it must be at least 20 characters long and must conform to the [Commit Message Body](#52-commit-message-body) format.

The `footer` is optional.
The [Commit Message Footer](#53-commit-message-footer) format describes what the footer is used for and the structure it must have.

### 5.1 Commit Message Header

```text
<type>(<scope>): <short summary>
 │        │             │
 │        │             └─⫸ Summary in present tense. Not capitalized. No period at the end.
 │        │
 │        └─⫸ Commit Scope.
 │
 └─⫸ Commit Type: build|ci|docs|feat|fix|perf|refactor|test
```
The `<type>` and `<short summary>` fields are mandatory; the `(<scope>)` field is optional.

#### 5.1.1 Type

Must be one of the following:

| Type         | Description                                                                 |
|--------------|-----------------------------------------------------------------------------|
| **build**    | Changes that affect the build system or external dependencies               |
| **ci**       | Changes to the CI configuration files and scripts (example: GitHub Actions) |
| **docs**     | Documentation-only changes                                                  |
| **feat**     | Introduces a new feature                                                    |
| **fix**      | A bug fix                                                                   |
| **perf**     | A code change that improves performance                                     |
| **refactor** | A code change that neither fixes a bug nor adds a feature                   |
| **test**     | Adding tests or correcting existing tests                                   |

#### 5.1.2 Scope

The scope should be the name of the Java module or package affected (as perceived by the person reading the changelog generated from commit messages).

There is one exception to the "use package name" rule:

* **none/empty string:** useful for `test` and `refactor` changes that are done across all modules (e.g., `test: add missing unit tests`) and for documentation changes that are not related to a specific module (e.g., `docs: fix typo in tutorial`).

#### 5.1.3 Summary

Use the short summary field to provide a succinct description of the change:

* use the imperative, present tense: "change" not "changed" nor "changes"
* don't capitalize the first letter
* no period at the end
* max 60 characters

### 5.2 Commit Message Body

As in the summary, use the imperative, present tense: "fix" not "fixed" nor "fixes".

Avoid lines longer than 72 characters in the commit message body.

Explain the motivation for the change in the commit message body.
This commit message should explain _why_ you are making the change.
You can include a comparison of the previous behavior with the new behavior in order to illustrate the impact of the change.

### 5.3 Commit Message Footer

The footer can contain information about breaking changes and deprecations and is also the place to reference GitHub issues and other PRs that this commit closes or is related to.

For example:

```text
BREAKING CHANGE: <breaking change summary>
<BLANK LINE>
<breaking change description + migration instructions>
<BLANK LINE>
<BLANK LINE>
Fixes #<issue number>
```

or

```text
DEPRECATED: <what is deprecated>
<BLANK LINE>
<deprecation description + recommended update path>
<BLANK LINE>
<BLANK LINE>
Closes #<pr number>
```

The breaking change section should start with the phrase `BREAKING CHANGE: ` followed by a _brief_ summary of the breaking change (max 55 characters), a blank line, and a detailed description of the breaking change that also includes migration instructions.

Similarly, the deprecation section should start with `DEPRECATED: ` followed by a short description of what is deprecated (max 60 characters), a blank line, and a detailed description of the deprecation that also mentions the recommended update path.

### 5.4 Revert commits

If the commit reverts a previous commit, it should begin with `revert: `, followed by the header of the reverted commit.

The commit message body should include:

* Information about the SHA of the commit being reverted in the following format: `This reverts commit <SHA>`
* A clear description of the reason for reverting the commit.

## 6. Writing Tests and Documentation

High-quality tests and clear documentation are essential for maintaining the project's stability and usability.

### 6.1 Writing Tests

All contributions that add or modify code must include corresponding tests.
The project uses JUnit categories to group tests based on their purpose and execution time.

#### 6.1.1 Test Categories

| Category               | Description                                                                                                                                                                                                                          |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Unit & Smoke Tests** | These are fast, reliable tests that verify individual components and critical application paths. They do not require a specific category annotation (`SmokeTest` is run by default) and are executed with the standard test command. |
| **Fuzzer Tests**       | These are long-running, resource-intensive tests designed to find stability or security issues by providing unexpected or random data. They are marked with the `@Category(FuzzerTest.class)` annotation and must be run separately. |
| **Integration Tests**  | These are browser-based tests that validate end-to-end functionality. They are located in the `test-suite` module and are designed to be run locally, not as part of the standard test suite for contributions.                      |


### 6.2 Running Tests

You can run different sets of tests using Maven's build lifecycle and profiles.

#### 6.2.1 Running the Standard Test Suite (Required for PRs)

To run all unit and smoke tests (which excludes fuzzer and browser-based integration tests), use the `test` phase.
This is the primary command you should run to validate your changes before submitting a pull request.

```shell
mvn -pl test-framework-core -am clean test
```

This command executes quickly and ensures that your changes have not introduced any regressions in the core logic.

#### 6.2.2 Running Fuzzer Tests

To run the specialized fuzzer tests, you must activate the `fuzzer-tests` Maven profile using the `-P` flag.
This will run *only* the tests categorized as `FuzzerTest`.

```shell
mvn -pl test-framework-core -am clean test -P fuzzer-tests
```

#### 6.2.3 Running Browser-Based Integration Tests

The browser-based integration tests are executed during the `verify` phase.
These tests require a local browser setup and are generally not required for contributions unless you are specifically working on the integration test suite itself.

```shell
mvn clean verify
```

> **Note:**
> You are not expected to run this command as part of a typical contribution. The CI system does not run these tests.

### 6.3 Documentation

If your changes affect user-facing aspects, internal architecture, or add new features, please update the relevant documentation (e.g., READMEs, Javadoc).
Clear documentation is as important as the code itself.

## 7. Submitting a Pull Request (PR)

1.  **Push Your Changes:**
    ```shell
    git push origin your-branch-name
    ```
2.  **Open a Pull Request:**
    * Go to your fork on GitHub (`https://github.com/<your-username>/test-framework`).
    * Click the "Compare & pull request" button for your recently pushed branch.
    * Ensure the base repository is `larrykramer/test-framework` and the base branch is `main`.
3.  **Describe Your PR:**
    * Provide a clear title and description for your pull request, adhering to the commit message guidelines for the PR title itself.
    * Explain the changes you've made and the problem they solve.
    * Link to any relevant issues (e.g., "Closes #123").
    * Include steps for reviewers to test your changes, if applicable.
4.  **Code Review:**
    * I will review your PR as soon as I can.
      Since this is a personal project, please be patient if it takes a little while to get a response.
      I will do my best to provide timely feedback.
    * Be prepared to address comments and make changes.
    * Ensure your PR adheres to the coding standards and commit message guidelines.
    * Engage in the discussion and be responsive to comments.
5.  **Merging:**
    * Once your PR is approved and passes any automated checks (CI builds), it will be merged into the main codebase.

## 8. Issue Tracking

* **Search Existing Issues:**
  Before opening a new issue, please check if a similar one already exists.
* **Be Clear and Specific:**
  When reporting a bug, include:
  * Steps to reproduce the bug.
  * Expected behavior.
  * Actual behavior.
  * Your environment (OS, Java version, the version of the Test Framework you are using).
  * Any relevant logs or screenshots.
* **Use Labels:**
  I will apply appropriate labels to categorize issues.
  If you have the necessary permissions, feel free to add them yourself.

## 9. Questions or Need Help?

If you have questions about contributing or need help, feel free to open a new topic in the [Discussions](https://github.com/larrykramer/test-framework/discussions) section.
I will get back to you as soon as I am able.

Thank you for contributing to the Test Framework!
Your efforts help make this project better for everyone.
