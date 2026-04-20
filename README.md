# Try Out Development Containers: Java

[![Open in Dev Containers](https://img.shields.io/static/v1?label=Dev%20Containers&message=Open&color=blue&logo=visualstudiocode)](https://vscode.dev/redirect?url=vscode://ms-vscode-remote.remote-containers/cloneInVolume?url=https://github.com/microsoft/vscode-remote-try-java)

A **development container** is a running container with a well-defined tool/runtime stack and its prerequisites. You can try out development containers with **[GitHub Codespaces](https://github.com/features/codespaces)** or **[Visual Studio Code Dev Containers](https://aka.ms/vscode-remote/containers)**.

This is a sample project that lets you try out either option in a few easy steps. We have a variety of other [vscode-remote-try-*](https://github.com/search?q=org%3Amicrosoft+vscode-remote-try-&type=Repositories) sample projects, too.

> **Note:** If you already have a Codespace or dev container, you can jump to the [Things to try](#things-to-try) section.

## Setting up the development container

### GitHub Codespaces
Follow these steps to open this sample in a Codespace:
1. Click the **Code** drop-down menu.
2. Click on the **Codespaces** tab.
3. Click **Create codespace on main**.

For more info, check out the [GitHub documentation](https://docs.github.com/en/free-pro-team@latest/github/developing-online-with-codespaces/creating-a-codespace#creating-a-codespace).

### VS Code Dev Containers

If you already have VS Code and Docker installed, you can click the badge above or [here](https://vscode.dev/redirect?url=vscode://ms-vscode-remote.remote-containers/cloneInVolume?url=https://github.com/microsoft/vscode-remote-try-java) to get started. Clicking these links will cause VS Code to automatically install the Dev Containers extension if needed, clone the source code into a container volume, and spin up a dev container for use.

Follow these steps to open this sample in a container using the VS Code Dev Containers extension:

1. If this is your first time using a development container, please ensure your system meets the pre-reqs (i.e. have Docker installed) in the [getting started steps](https://aka.ms/vscode-remote/containers/getting-started).

2. To use this repository, you can either open the repository in an isolated Docker volume:

    - Press <kbd>F1</kbd> and select the **Dev Containers: Try a Sample...** command.
    - Choose the "Java" sample, wait for the container to start, and try things out!
        > **Note:** Under the hood, this will use the **Dev Containers: Clone Repository in Container Volume...** command to clone the source code in a Docker volume instead of the local filesystem. [Volumes](https://docs.docker.com/storage/volumes/) are the preferred mechanism for persisting container data.

   Or open a locally cloned copy of the code:

   - Clone this repository to your local filesystem.
   - Press <kbd>F1</kbd> and select the **Dev Containers: Open Folder in Container...** command.
   - Select the cloned copy of this folder, wait for the container to start, and try things out!

## Things to try

Once you have this sample opened, you'll be able to work with it like you would locally.

Some things to try:

1. **Edit:**
   - Open `src/main/java/com/mycompany/app/App.java`.
   - Try adding some code and check out the language features.
   - Make a spelling mistake and notice it is detected. The [Code Spell Checker](https://marketplace.visualstudio.com/items?itemName=streetsidesoftware.code-spell-checker) extension was automatically installed because it is referenced in `.devcontainer/devcontainer.json`.
   - Also notice that the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) is installed. The JDK is in the `mcr.microsoft.com/devcontainers/java` image and Dev Container settings and metadata are automatically picked up from [image labels](https://containers.dev/implementors/reference/#labels).

2. **Terminal:** Press <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>\`</kbd> and type `uname` and other Linux commands from the terminal window.

3. **Build, Run, and Debug:**
   - Open `src/main/java/com/mycompany/app/App.java`.
   - Add a breakpoint.
   - Press <kbd>F5</kbd> to launch the app in the container.
   - Once the breakpoint is hit, try hovering over variables, examining locals, and more.

4. **Run a Test:**
   - Open `src/test/java/com/mycompany/app/AppTest.java`.
   - Put a breakpoint in a test.
   - Click the `Debug Test` in the Code Lens above the function and watch it hit the breakpoint.

5. **Install Node.js using a Dev Container Feature:**
   - Press <kbd>F1</kbd> and select the **Dev Containers: Configure Container Features...** or **Codespaces: Configure Container Features...** command.
   - Type "node" in the text box at the top.
   - Check the check box next to "Node.js (via nvm) and yarn" (published by devcontainers) 
   - Click OK
   - Press <kbd>F1</kbd> and select the **Dev Containers: Rebuild Container** or **Codespaces: Rebuild Container** command so the modifications are picked up.
  
6. **Run postgres database services: Healdth Care Professional**
   - create: docker run -d --name postgres-healthcareprof -p 5433:5432 -e POSTGRES_USER=root -e POSTGRES_PASSWORD=<psw> -e POSTGRES_DB=healthcare_db -v postgres_data:/var/lib/postgresql/data postgres:latest
   - Shell: docker exec -it postgres-healthcareprof psql -U root -d healthcare_prof_db
   - start: docker start postgres-healthcareprof
7. **Run mysql database services: Medical Sales Representative**
    - MedicalSalesRe: docker run -d --name mysql-medicalsalesrep -p 3307:3306 -e MYSQL_ROOT_PASSWORD=riverplate -e MYSQL_DATABASE=medical_sales_db -v mysql_data:/var/lib/mysql mysql:latest
    - Shell:  docker exec -it mysql-medicalsalesrep mysql -u root -p
    - start:  docker start mysql-medicalsalesrep

## OWASP Security Implementation

This project implements OWASP Top 10 security best practices for Spring Boot applications. The implementation covers the following security areas:

### 🔒 **A01:2021 - Broken Access Control**
- **Spring Security Configuration**: Implemented comprehensive security config with proper authorization
- **Role-based Access**: Configured user roles and permissions
- **Session Management**: Stateless sessions with proper timeout and cookie security

### 🔑 **A02:2021 - Cryptographic Failures**
- **Password Encryption**: BCrypt password encoder with strength 12
- **Secure Headers**: HSTS, CSP, X-Frame-Options, and other security headers
- **HTTPS Enforcement**: Secure cookie configuration

### 🛡️ **A03:2021 - Injection**
- **Input Validation**: Jakarta Validation with `@Valid` annotations on all endpoints
- **SQL Injection Prevention**: Parameterized queries in JPA repositories
- **XSS Prevention**: Proper output encoding and Content Security Policy

### 🚫 **A04:2021 - Insecure Design**
- **Domain-Driven Design**: Clean architecture separating business logic from infrastructure
- **Input Sanitization**: Comprehensive validation at domain level
- **Error Handling**: Secure exception handling that doesn't leak sensitive information

### 🔧 **A05:2021 - Security Misconfiguration**
- **Security Headers**: Comprehensive security headers configuration
- **Actuator Security**: Limited exposure of sensitive endpoints
- **Environment Configuration**: Secure property management

### 📦 **A06:2021 - Vulnerable Components**
- **OWASP Dependency Check**: Automated vulnerability scanning in CI/CD
- **Regular Updates**: Latest stable versions of Spring Boot and dependencies
- **Dependency Auditing**: Maven plugin for continuous vulnerability monitoring

### 🔐 **A07:2021 - Identification & Authentication Failures**
- **Spring Security**: Proper authentication mechanisms
- **Password Policies**: Strong password requirements
- **Session Security**: Secure session management

### 📊 **A08:2021 - Software Integrity Failures**
- **Input Validation**: Comprehensive validation of all inputs
- **Data Integrity**: Domain validation ensuring data consistency
- **Audit Logging**: Security event logging

### 📋 **A09:2021 - Security Logging & Monitoring**
- **Actuator Endpoints**: Health checks and metrics monitoring
- **Error Logging**: Secure error handling and logging
- **Rate Limiting**: Protection against abuse with configurable limits

### 🌐 **A10:2021 - Server-Side Request Forgery (SSRF)**
- **CORS Configuration**: Strict CORS policies
- **Input Validation**: URL validation to prevent SSRF attacks
- **Network Security**: Proper firewall and network segmentation

### 🛠️ **Security Features Implemented**

#### **Rate Limiting**
```java
// Configured with Bucket4j for distributed rate limiting
// Allows 100 requests per minute with burst capacity
```

#### **Input Validation**
```java
@PostMapping("/create")
public ResponseEntity<Object> createMedicalSalesRep(@Valid @RequestBody CreateMedicalSalesRepInputDTO inputDTO)
```

#### **Security Headers**
```properties
# Security headers configured in SecurityConfig
- Strict-Transport-Security
- X-Frame-Options: DENY
- X-Content-Type-Options
- Referrer-Policy
- Permissions-Policy
```

#### **Dependency Vulnerability Scanning**
```xml
<!-- OWASP Dependency Check Plugin -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
    </configuration>
</plugin>
```

### 🚀 **Running Security Checks**

1. **Dependency Vulnerability Scan**:
   ```bash
   mvn org.owasp:dependency-check-maven:check
   ```

2. **Run Application with Security**:
   ```bash
   mvn spring-boot:run
   ```

3. **Test Rate Limiting**:
   ```bash
   # Make multiple requests to test rate limiting
   curl -X GET http://localhost:8085/api/v1/medicalsalesrep/list
   ```

### 📋 **Security Testing Checklist**

- [ ] Authentication works correctly
- [ ] Authorization prevents unauthorized access
- [ ] Input validation blocks malicious inputs
- [ ] HTTPS is enforced in production
- [ ] Security headers are present
- [ ] Rate limiting prevents abuse
- [ ] Error messages don't leak sensitive information
- [ ] Dependencies are free of known vulnerabilities
- [ ] Session management is secure
- [ ] CORS policies are restrictive

### 🔍 **Monitoring & Alerting**

The application exposes security-relevant metrics via Spring Boot Actuator:
- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

Configure monitoring tools to alert on:
- High error rates
- Rate limit violations
- Authentication failures
- Unusual traffic patterns

### 📚 **Additional Security Resources**

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)
- [Spring Boot Security Best Practices](https://spring.io/guides/topicals/spring-security-architecture/)

## Contributing

This project welcomes contributions and suggestions. Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.microsoft.com.

When you submit a pull request, a CLA-bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., label, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## License

Copyright © Microsoft Corporation All rights reserved.<br />
Licensed under the MIT License. See LICENSE in the project root for license information.
