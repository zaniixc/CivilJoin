# CivilJoin

CivilJoin is a JavaFX-based government forum application that provides a unified interface for citizen engagement and administrative control. The application enables citizens to register, authenticate, post content, and interact with government services through a clean, responsive interface.

## Project Setup

- Building is done on IntelliJ IDEA Ultimate.

### Prerequisites

- Java 17 or higher
- MySQL Database
- Maven (for building)

### Step 1: Database Setup

1. Download, Install and Configure MySQL `https://dev.mysql.com/downloads/installer/` for In-depth guide `https://www.youtube.com/watch?v=wgRwITQHszU`
2. Run the SQL script located at `src/main/resources/master_schema.sql` to set up the database schema and initial data
3. Run through powershell. For windows: `Get-Content src/main/resources/master_schema.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p`

### Step 2: Running the Application

1. Download and Install Java 23 `https://download.oracle.com/java/23/archive/jdk-23_windows-x64_bin.exe` Make sure the Installation directory are set to default.
2. Download Maven `https://dlcdn.apache.org/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip`
3. Adding Maven for Environment Variables this is so we can run it through powershell using `mvn` command. Search for `edit the system environment variables` at the start menu, select `Environment Variables`, click `Path`, select `Edit`, click `New`, now make sure to extract the .zip file and select your maven bin folder you just downloaded `C:\Users\%user%\Downloads\apache-maven-3.9.10-bin\apache-maven-3.9.10\bin`, locate and paste the `bin` folder click enter then `OK`
4. Clone the repository
5. Configure the database connection see the `Step 1` if needed
6. Open the powershell locate the directory of the repository `"CivilJoin"`
3. Make sure set the enviroment of Java to the directory `$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"` for command
4. Build the project with Maven:
   ```
   mvn clean package
   ```
4. Run the application:
   ```
   mvn javafx:run
   ```

## Login Information

### User
- Username: testuser
- Password: user123

### User Registration
- Use one of the following 16-digit Key IDs for registration:
  - 1234567890123456
  - 2345678901234567
  - 3456789012345678

## Initial Task List

Based on the PRD, the following tasks need to be completed:

### Sprint 1: Core Authentication and User Management

1. **Authentication**
   - [x] Basic login/register UI
   - [x] Simple password handling (Note: using development-only implementation)
   - [ ] Session management with timeout
   - [ ] Key ID validation

> **Note:** The current implementation uses a simplified password handling approach for development. 
> A proper implementation with BCrypt will be implemented in a future sprint.

2. **User Dashboard**
   - [x] Basic dashboard structure
   - [ ] Responsive grid layout for posts
   - [ ] Post viewing functionality
   - [ ] Attachment download functionality

3. **Database Integration**
   - [x] Database schema design
   - [ ] DAO implementation for User
   - [ ] DAO implementation for Key ID
   - [ ] Database connection pooling

### Sprint 2: Content Management

1. **Post Management**
   - [ ] Post creation UI
   - [ ] Post deletion functionality
   - [ ] Post search and filtering
   - [ ] Timeline pagination

2. **Feedback System**
   - [ ] Feedback submission form
   - [ ] Feedback listing for admins
   - [ ] Feedback response functionality

3. **Administrative Interface**
   - [ ] Admin panel UI
   - [ ] User management tools
   - [ ] Key ID generation and management
   - [ ] Activity log monitoring

### Sprint 3: User Experience and Security

1. **User Settings**
   - [ ] Profile information editing
   - [ ] Password change functionality
   - [ ] Theme toggle (light/dark)
   - [ ] Notification preferences

2. **Security Features**
   - [ ] Session timeout implementation
   - [ ] Brute force protection
   - [ ] Input validation and sanitization
   - [ ] Role-based access control

3. **Accessibility**
   - [ ] Keyboard navigation
   - [ ] Screen reader support
   - [ ] High contrast mode
   - [ ] Multi-language support

## Architecture

The application follows the MVC (Model-View-Controller) pattern:

- **Models**: Represent the data structures and business logic
- **Views**: FXML files defining the UI layout
- **Controllers**: Handle user interactions and connect models with views

## Learning Resources

- [JavaFX Documentation](https://openjfx.io/javadoc/17/)
- [BootstrapFX](https://github.com/kordamp/bootstrapfx)
- [ControlsFX](https://github.com/controlsfx/controlsfx)
- [FormsFX](https://github.com/dlsc-software-consulting-gmbh/FormsFX)
- [TilesFX](https://github.com/HanSolo/tilesfx) 