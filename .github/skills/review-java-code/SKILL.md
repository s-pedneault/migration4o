---
name: review-java-code
description: Review Java code and ensure it is up to par with our standards. Use this skill after making changes to Java code.
---

# Reviewing Java Code

When reviewing Java code, ensure that it adheres to our coding standards and best practices. Pay attention to the following aspects:

1. **Modular code**: Ensure we don't use long methods or classes that do too much. Each method and class should have a single responsibility. Create new classes to make code more modular, purpose-specific, and easier to understand.
1. **State-of-the-art code**: Use state-of-the-art code techniques.
1. **Avoid redundant code**: Eliminate duplicate code and unnecessary complexity. Ensure that the code is efficient and maintainable. If we have redundant code, we need to move it to a utility class to have ONE source of truth.
1. **Make sure we use the best possible code**: Make sure any new code does not reinvent the wheel; search for existing utilities or classes that already provide the functionality we need. If we have existing code that can be reused, use it instead of creating new code. If the existing code is mostly suitable but has some issues, ask me what to do.
1. **User strong typing**: Use strong typing to prevent type-related errors and improve code clarity. Do not pass around untyped data structures like maps or lists when a well-defined class can be used instead.
1. **Ensure safety against unexpected errors**: Implement proper error handling and validation to prevent runtime exceptions and ensure robust code. Make sure that errors are propagated to the user. No silent failures.
1. **Use clear and descriptive names**: Choose meaningful names for variables, methods, and classes to enhance code readability and maintainability. Avoid abbreviations and ensure that names accurately reflect their purpose and usage.
1. **Prefer moving numerous arguments to an attributes class**: When a method has many parameters, consider encapsulating them in a dedicated attributes class to improve readability and maintainability.
1. **Avoid code sync issues**: Avoid referring to objects by indexes or keys that can change over time. Prefer creating classes that encapsulate the data and behavior, and use those classes instead of raw data structures. This helps prevent issues where the structure of the data changes but the code that accesses it does not.
1. **Avoid hacks**: Avoid using hacks or workarounds that may solve a problem in the short term but can lead to maintenance issues in the long run. Instead, strive for clean and maintainable code that follows best practices and design principles.
1. **Ask before making assumptions**: If you are unsure about the intent or functionality of a piece of code, ask for clarification before making changes. This helps ensure that your changes align with the original design and intent of the code.
1. **Ask before leaving legacy code**: We prefer clean, modern code. If you are considering leaving legacy code in place, ask for feedback to ensure that it is the best course of action. Legacy code can be a source of technical debt, so it's important to evaluate whether it can be refactored or removed to improve the overall quality of the codebase.
1. **Evaluate code paths**: When reviewing code, consider the various code paths and scenarios that may arise. Ensure that the code handles edge cases and potential errors gracefully, and that it is robust enough to handle unexpected inputs or conditions. If we have multiple code paths for a feature, justify why we need them, and ask for confirmation.
1. **Ensure that we cause no data loss**: Our core business is data processing, so we must ensure that our code does not cause data loss. Review the code to ensure that it properly handles data and does not introduce any risks of data loss. If there are any potential risks, explain them thoroughly and ask for instructions.
