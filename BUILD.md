# Build and Deployment Instructions

## Prerequisites Check

```bash
# Verify JDK 21
java -version
# Expected: openjdk version "21.x.x" or java version "21.x.x"

# Verify Maven
mvn -version
# Expected: Apache Maven 3.8.x or higher

# Verify MySQL
mysql --version
# Expected: mysql Ver 8.0.x or higher
```

## Complete Build Process

### 1. Initialize Database

```bash
# Start MySQL if not running
sudo systemctl start mysql

# Create and initialize database
mysql -u root -p << 'EOF'
CREATE DATABASE IF NOT EXISTS aidebate DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF

# Import schema
mysql -u root -p aidebate < aidebate-start/src/main/resources/db/schema.sql

# Import data
mysql -u root -p aidebate < aidebate-start/src/main/resources/db/data.sql

# Verify tables
mysql -u root -p aidebate -e "SHOW TABLES;"
```

### 2. Configure Application

```bash
# Set environment variables
export MYSQL_PASSWORD="your_mysql_password"
export ALIBABA_AI_API_KEY="your_api_key_if_available"

# Or create application-local.yml
cat > aidebate-start/src/main/resources/application-local.yml << 'EOF'
spring:
  datasource:
    password: your_mysql_password
  ai:
    alibaba:
      api-key: your_api_key
EOF
```

### 3. Build Project

```bash
# Clean build (first time)
mvn clean install -DskipTests

# Full build with tests
mvn clean install

# Quick build (skip tests)
mvn clean package -DskipTests
```

### 4. Run Application

```bash
# Option 1: Maven Spring Boot Plugin
cd aidebate-start
mvn spring-boot:run

# Option 2: Java JAR
cd aidebate-start/target
java -jar aidebate-start-1.0.0-SNAPSHOT.jar

# Option 3: With specific profile
java -jar aidebate-start-1.0.0-SNAPSHOT.jar --spring.profiles.active=local

# Option 4: With custom port
java -jar aidebate-start-1.0.0-SNAPSHOT.jar --server.port=8081
```

### 5. Verify Deployment

```bash
# Check if application is running
curl http://localhost:8080/api/health

# Should return:
# {"status":"UP","application":"AI Debate Simulator","version":"1.0.0-SNAPSHOT"}

# Test database connection
curl http://localhost:8080/api/topics

# Should return JSON array of debate topics
```

## Compilation and Debugging

### Debug Mode

```bash
# Run with debug logging
mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.com.aidebate=DEBUG"

# Remote debugging
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Then attach debugger to localhost:5005
```

### Common Build Issues

#### Issue: Module not found
```bash
# Solution: Install parent POM first
mvn clean install -N

# Then build all modules
mvn clean install
```

#### Issue: Test failures
```bash
# Skip tests temporarily
mvn clean install -DskipTests

# Run specific test
mvn test -Dtest=ContentModerationServiceTest
```

#### Issue: Dependency resolution
```bash
# Force update
mvn clean install -U

# Purge and rebuild
mvn dependency:purge-local-repository
mvn clean install
```

## Production Deployment Checklist

- [ ] Database created and initialized
- [ ] All environment variables set
- [ ] AI API keys configured
- [ ] Logging configured
- [ ] Build successful (mvn clean install)
- [ ] Health check endpoint responds
- [ ] Database connectivity verified
- [ ] All modules compiled successfully
- [ ] Static resources accessible
- [ ] Security configurations reviewed

## Performance Tuning

### JVM Options

```bash
java -Xms512m -Xmx2048m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar aidebate-start-1.0.0-SNAPSHOT.jar
```

### Database Connection Pool

Already configured in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
```

## Monitoring

### Application Logs

```bash
# View logs
tail -f logs/aidebate.log

# Search for errors
grep -i error logs/aidebate.log

# Monitor startup
tail -f logs/aidebate.log | grep -i "started"
```

### Health Endpoints

```bash
# Application health
curl http://localhost:8080/api/health

# Spring actuator (if enabled)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

## Maintenance

### Database Backup

```bash
# Backup database
mysqldump -u root -p aidebate > aidebate_backup_$(date +%Y%m%d).sql

# Restore database
mysql -u root -p aidebate < aidebate_backup_20231214.sql
```

### Update Application

```bash
# Pull latest code
git pull origin main

# Rebuild
mvn clean install

# Restart application
# (Stop current instance and start new one)
```

## Success Indicators

✅ Application starts without errors
✅ Logs show "Started AiDebateApplication in X seconds"
✅ Health endpoint returns {"status":"UP"}
✅ Topics API returns debate topics
✅ Frontend loads at http://localhost:8080
✅ Database queries execute successfully
✅ No error messages in logs

## Quick Test Script

```bash
#!/bin/bash
echo "Testing AI Debate Simulator..."

# Test 1: Health Check
echo "1. Health Check..."
curl -s http://localhost:8080/api/health | grep -q "UP" && echo "✅ PASS" || echo "❌ FAIL"

# Test 2: Topics API
echo "2. Topics API..."
curl -s http://localhost:8080/api/topics | grep -q "topic" && echo "✅ PASS" || echo "❌ FAIL"

# Test 3: Frontend
echo "3. Frontend..."
curl -s http://localhost:8080 | grep -q "AI DEBATE SIMULATOR" && echo "✅ PASS" || echo "❌ FAIL"

echo "Testing complete!"
```

Save as `test.sh`, make executable with `chmod +x test.sh`, and run with `./test.sh`
