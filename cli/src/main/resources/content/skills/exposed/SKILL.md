---
name: exposed
description: >-
  Use when writing Kotlin/JVM database code with JetBrains Exposed:
  module selection (`exposed-core`/`-jdbc`/`-r2dbc`/`-dao`/`-java-time`/
  `-json`/`-spring-boot-starter`), `Database.connect` with HikariCP,
  `transaction { }` and suspend `newSuspendedTransaction` with isolation
  and maxAttempts, table definitions (`Table`, `IntIdTable`/`LongIdTable`/
  `UUIDTable`/`CompositeIdTable`), column types (numeric, text, UUID,
  date/time, enum, array, JSON, references with `onDelete`/`onUpdate`),
  DSL CRUD (`insert`/`upsert`/`batchInsert`/`update`/`deleteWhere`/
  `*Returning`), querying (`selectAll`/`where`/`groupBy`/`orderBy`/
  `forUpdate`), joins (`innerJoin`/`leftJoin`/`crossJoin`), DAO entities
  (`IntEntity`/`UUIDEntity` with `EntityClass`, `var by col`,
  `referencedOn`/`referrersOn`/`via`, `.load`/`.with` eager loading),
  `SchemaUtils`, H2 testing, and Spring Boot integration.
---

## When to use this skill

- Any Kotlin/JVM database code built on `org.jetbrains.exposed:*`.
- Choosing between DSL and DAO, or bridging the two.
- Wiring Exposed behind a coroutine-friendly service.
- Schema setup for tests (H2 or Testcontainers-backed Postgres).
- Writing a custom column type, or resolving a cross-DB portability
  issue.

## When NOT to use this skill

- Very simple CRUD apps that can run on Spring Data JPA — the Exposed
  learning curve isn't worth it.
- Pure analytics workloads — go through JDBC + a SQL string, or use a
  dedicated library (jOOQ, Doma).

## Version split

Exposed had a package rename around the move to 1.x:

- **0.x** — `org.jetbrains.exposed.sql.*`,
  `org.jetbrains.exposed.sql.transactions.*`. Most code in the wild.
- **1.x** — `org.jetbrains.exposed.v1.core.*`,
  `org.jetbrains.exposed.v1.jdbc.*`,
  `org.jetbrains.exposed.v1.r2dbc.*`. Adds R2DBC, tightens module
  boundaries.

Imports and artifact IDs differ. Check which major version is applied
before following snippets here — the examples below use 0.x package
paths (still dominant); the `v1` equivalents are noted where they
diverge significantly.

## Modules — pick explicitly

| Artifact                           | Purpose                                             |
|------------------------------------|-----------------------------------------------------|
| `exposed-core`                     | DSL, types, SQL generation. Always required.        |
| `exposed-jdbc`                     | JDBC transport. Required for synchronous APIs.      |
| `exposed-r2dbc`                    | Reactive transport (1.x). Excludes DAO.             |
| `exposed-dao`                      | DAO layer. JDBC only — no R2DBC support.            |
| `exposed-java-time`                | `date`/`datetime`/`timestamp` on JSR-310.           |
| `exposed-kotlin-datetime`          | Same but on `kotlinx-datetime`.                     |
| `exposed-json`                     | `json`/`jsonb` columns.                             |
| `exposed-crypt`                    | Encrypted column types.                             |
| `exposed-money`                    | `javax.money.MonetaryAmount` columns.               |
| `exposed-spring-boot-starter`      | Spring Boot auto-configuration.                     |
| `spring-transaction` / `spring7-transaction` | Spring `TransactionManager` bridge.       |
| `exposed-migration`                | Alpha Flyway/Liquibase-style migrations.            |

Pull only what you use; the DAO jar is a separate opt-in.

Supported databases: PostgreSQL, MySQL, MariaDB, Oracle, SQL Server,
SQLite, H2.

## Connecting

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

val ds = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/app"
        username = "app"
        password = System.getenv("DB_PASSWORD")
        maximumPoolSize = 10
        isReadOnly = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
    },
)
val db: Database = Database.connect(ds)
```

Alternative forms:

```kotlin
// Direct URL (no pool — avoid in production)
Database.connect(
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    driver = "org.h2.Driver",
    user = "sa",
    password = "",
)

// Multiple databases
val primary = Database.connect(primaryDs)
val replica = Database.connect(replicaDs)
TransactionManager.defaultDatabase = primary
```

`Database.connect` does **not** open a connection — it registers a
data source. Connections are checked out per transaction.

## Transactions

```kotlin
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

// Blocking
transaction(db) {
    // DSL / DAO operations
}

// Suspending
suspend fun findName(id: UUID): String? =
    newSuspendedTransaction(Dispatchers.IO, db) {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.get(Users.name)
    }
```

### Parameters

| Parameter               | Meaning                                                  |
|-------------------------|----------------------------------------------------------|
| `db`                    | Target `Database`; omit to use the default.              |
| `transactionIsolation`  | `Connection.TRANSACTION_READ_COMMITTED` / `_REPEATABLE_READ` / `_SERIALIZABLE` / `_READ_UNCOMMITTED`. |
| `readOnly`              | Passes read-only hint to the driver.                     |
| `maxAttempts`           | Retry count on `SQLException`.                           |
| `minRepetitionDelay` / `maxRepetitionDelay` | Exponential-backoff window for retries. |
| `queryTimeout`          | Seconds before statement timeout.                        |

### Nested transactions

```kotlin
db.useNestedTransactions = true     // opt-in per Database

transaction {
    // outer
    transaction {
        // inner uses SAVEPOINT — can roll back independently
    }
}
```

Default behavior: inner blocks share the outer resources; any
rollback affects the whole tree.

### Savepoints

```kotlin
transaction {
    val sp = connection.setSavepoint("before_risky")
    runCatching { risky() }.onFailure { connection.rollback(sp) }
}
```

### Raw SQL

```kotlin
transaction {
    exec("VACUUM ANALYZE users")
    exec(
        stmt = "SELECT count(*) FROM users WHERE age > ?",
        args = listOf(IntegerColumnType() to 18),
    ) { rs -> if (rs.next()) rs.getInt(1) else 0 }
}
```

### Entities leave the transaction

Query results and DAO entities are only safe to use inside the
transaction that loaded them. Either map to DTOs before returning, or
switch to `eagerLoading = true` on `text`/`blob` columns.

### Coroutines — never mix `transaction { }` and `withContext`

```kotlin
// WRONG — blocks a thread you borrowed from a coroutine pool
withContext(Dispatchers.IO) { transaction { ... } }

// RIGHT — newSuspendedTransaction suspends correctly
newSuspendedTransaction(Dispatchers.IO) { ... }
```

Nested suspended transactions reuse the outer connection unless you
pass a different `Database`.

## Table definitions

```kotlin
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object Cities : Table("cities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age").check { it greaterEq intLiteral(0) }
    val active = bool("active").default(true)
    val cityId = integer("city_id")
        .references(Cities.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object UserRoles : Table("user_roles") {
    val userId = uuid("user_id").references(Users.id)
    val role = varchar("role", 30)
    override val primaryKey = PrimaryKey(userId, role, name = "PK_user_roles")
}
```

### Column constraints

| Modifier                    | Effect                                          |
|-----------------------------|-------------------------------------------------|
| `.nullable()`               | Allow `NULL`. Return type becomes `T?`.         |
| `.default(x)`               | SQL-side default literal.                       |
| `.defaultExpression(expr)`  | SQL-side default from an expression (e.g. `CURRENT_TIMESTAMP`). |
| `.clientDefault { }`        | Client-side default, computed on insert.        |
| `.autoIncrement()`          | Auto-increment for integer PKs.                 |
| `.uniqueIndex()`            | Unique single-column index.                     |
| `.index()`                  | Non-unique index.                               |
| `.references(col, onDelete, onUpdate)` | Foreign key with cascade options.    |
| `.check(name) { predicate }`| `CHECK` constraint.                             |
| `.entityId()`               | Wrap as `EntityID<T>` (DAO).                    |

Composite indexes:

```kotlin
init {
    index(isUnique = true, Users.email, Users.active)
}
```

### Column types — full surface

| Builder                     | SQL                                       |
|-----------------------------|-------------------------------------------|
| `integer` / `long` / `short` / `byte` | `INT` / `BIGINT` / `SMALLINT` / `TINYINT` |
| `uinteger` / `ulong` / `ushort` / `ubyte` | unsigned variants              |
| `float` / `double`          | `FLOAT` / `DOUBLE`                        |
| `decimal(p, s)`             | `DECIMAL(p, s)` → `BigDecimal`            |
| `bool`                      | `BOOLEAN`                                 |
| `varchar(name, length)`     | `VARCHAR(length)`                         |
| `char(name, length)`        | `CHAR(length)`                            |
| `text(name)`                | `TEXT`                                    |
| `binary(name, length)`      | `VARBINARY(length)`                       |
| `blob(name)`                | `BLOB`                                    |
| `uuid(name)`                | DB-native UUID or `BINARY(16)`            |
| `date` / `datetime` / `timestamp` / `time` | JSR-310 via `exposed-java-time`. |
| `enumeration(name, enumClass)` | Stored as ordinal.                     |
| `enumerationByName(name, len, enumClass)` | Stored as string.          |
| `array(name, elementType)`  | Postgres arrays.                          |
| `json(name, serialize, deserialize)` | `JSON` (via `exposed-json`).     |
| `jsonb(name, …)`            | Postgres `JSONB`.                         |

Custom types: subclass `ColumnType<T>` and register via
`registerColumn`.

## DSL — CRUD

### Insert

```kotlin
// Single row, typed return
val newId: UUID = Users.insert {
    it[email] = "ada@example.com"
    it[age] = 37
    it[cityId] = munichId
} get Users.id

// IdTable → get the EntityID
val entityId: EntityID<Int> = Authors.insertAndGetId { it[name] = "Ada" }

// INSERT ... SELECT
Users.insert(
    Users.selectAll()
        .where { Users.active eq true }
        .map { ... }   // not typical; use the Query overload
)

// Skip duplicates (MySQL, MariaDB, PostgreSQL, SQLite)
Users.insertIgnore { it[email] = "ada@example.com"; it[age] = 37 }

// Batch — one statement
Users.batchInsert(records, shouldReturnGeneratedValues = false) { record ->
    this[Users.email] = record.email
    this[Users.age]   = record.age
}

// UPSERT (INSERT ... ON CONFLICT / MERGE)
Users.upsert(
    onUpdate = { it[Users.age] = Users.age + 1 },
    onUpdateExclude = listOf(Users.createdAt),
) {
    it[email] = "ada@example.com"
    it[age] = 37
}

// REPLACE INTO (SQLite, MySQL, MariaDB)
Users.replace {
    it[id] = existingId
    it[email] = "new@x.com"
    it[age] = 40
}

// RETURNING clauses (PostgreSQL, SQLite, MariaDB for insert/delete)
val row = Users.insertReturning(listOf(Users.id, Users.createdAt)) {
    it[email] = "ada@example.com"
    it[age] = 37
}.single()
```

`batchInsert` defaults to `shouldReturnGeneratedValues = true`, which
defeats batching on some drivers — flip it off for bulk loads.

### Update

```kotlin
val rows: Int = Users.update({ Users.email eq "ada@example.com" }) {
    it[age] = 38
    with(SqlExpressionBuilder) { it.update(loginCount, loginCount + 1) }
}

val returned = Users.updateReturning(
    where = { Users.active eq false },
    returning = listOf(Users.id),
) {
    it[active] = true
}
```

### Delete

```kotlin
Users.deleteWhere { Users.active eq false }           // returns rows affected
Users.deleteIgnoreWhere { Users.email eq "dup" }       // MySQL, MariaDB
Users.deleteAll()                                      // truncate alt; returns count

// From a join
(Users innerJoin Cities)
    .delete(Users, where = { Cities.name eq "obsolete" })
```

## DSL — querying

```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.*

transaction {
    // All columns
    val adults = Users.selectAll()
        .where { Users.age greaterEq 18 }
        .orderBy(Users.age to SortOrder.DESC)
        .limit(50)
        .offset(100)
        .toList()

    // Specific columns
    val emails = Users.select(Users.email, Users.age)
        .where { Users.active eq true }
        .map { it[Users.email] to it[Users.age] }

    // DISTINCT
    Users.selectAll().withDistinct().toList()

    // DISTINCT ON (PostgreSQL, H2)
    Users.selectAll().withDistinctOn(Users.cityId).toList()

    // Group / having
    Users.select(Users.cityId, Users.id.count())
        .groupBy(Users.cityId)
        .having { Users.id.count() greater 10L }
        .toList()

    // Conditional where
    var query = Users.selectAll()
    if (filterActive) query = query.andWhere { Users.active eq true }
    if (searchTerm != null) query = query.andWhere { Users.email like "%$searchTerm%" }

    // Pessimistic lock
    Users.selectAll().where { Users.id eq id }.forUpdate().singleOrNull()
}
```

### Predicate operators

| Category         | Operators                                                           |
|------------------|---------------------------------------------------------------------|
| Equality         | `eq`, `neq`, `isNull()`, `isNotNull()`, `isDistinctFrom`, `isNotDistinctFrom` |
| Ordering         | `less`, `lessEq`, `greater`, `greaterEq`, `.between(a, b)`          |
| Logical          | `and`, `or`, `not`, `andIfNotNull`, `orIfNotNull`, `compoundAnd()`, `compoundOr()` |
| Text             | `like`, `notLike`, `regexp`, `match` (MySQL/MariaDB)                |
| Collections      | `inList`, `notInList`, `inSubQuery`, `notInSubQuery`, `anyFrom(...)`, `allFrom(...)` |
| Existence        | `exists(subquery)`, `notExists(subquery)`                           |

### Subqueries & expressions

```kotlin
val lonelyCities = Cities.selectAll().where {
    notExists(Users.selectAll().where { Users.cityId eq Cities.id })
}

// Expose a subquery as a column expression
val userCount = wrapAsExpression<Int>(
    Users.select(Users.id.count()).where { Users.cityId eq Cities.id },
)
Cities.select(Cities.name, userCount).toList()

// CASE
val bucket = case()
    .When(Users.age less 18, stringLiteral("minor"))
    .When(Users.age less 65, stringLiteral("adult"))
    .Else(stringLiteral("senior"))

// Window
Users.select(Users.id, Users.age.avg().over().partitionBy(Users.cityId))
```

### Aliases

```kotlin
val alias = Users.alias("u2")
val older = alias.select(alias[Users.email])
    .where { alias[Users.age] greater 30 }
    .toList()

val sub = Users.select(Users.cityId, Users.id.count().alias("c"))
    .groupBy(Users.cityId)
    .alias("sub")
sub.select(sub[Users.cityId], sub["c"]).toList()
```

## Joins

```kotlin
// Implicit on FK
(Users innerJoin Cities).selectAll().toList()

// Explicit on/condition
val q = Users.join(Cities, JoinType.LEFT,
    onColumn = Users.cityId,
    otherColumn = Cities.id,
    additionalConstraint = { Cities.active eq true },
).select(Users.email, Cities.name)

// leftJoin / rightJoin / fullJoin / crossJoin via extension
(Users leftJoin Cities).selectAll()
```

Use `.adjustColumnSet { ... }` / `.adjustSelect { ... }` when building
queries dynamically without reconstructing them.

## DAO

### Table types

| Base                | Id type                        |
|---------------------|--------------------------------|
| `IntIdTable`        | `EntityID<Int>` auto-increment |
| `LongIdTable`       | `EntityID<Long>` auto-increment|
| `UUIDTable`         | `EntityID<UUID>`               |
| `CompositeIdTable`  | multi-column primary key       |

### Entities

```kotlin
object Authors : IntIdTable("authors") {
    val name = varchar("name", 100)
    val country = varchar("country", 3).nullable()
}

object Books : IntIdTable("books") {
    val title = varchar("title", 200)
    val authorId = reference("author_id", Authors)   // NOT NULL FK
    val publisherId = optReference("pub_id", Publishers)  // NULLable FK
}

class Author(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Author>(Authors)
    var name by Authors.name
    var country by Authors.country
    val books by Book referrersOn Books.authorId      // one-to-many
}

class Book(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Book>(Books)
    var title by Books.title
    var author by Author referencedOn Books.authorId        // many-to-one
    var publisher by Publisher optionalReferencedOn Books.publisherId
}
```

Relationship helpers:

- `referencedOn` / `optionalReferencedOn` — child's FK delegate.
- `referrersOn` / `optionalReferrersOn` — parent's reverse collection.
- `backReferencedOn` — one-to-one reverse.
- `via` — many-to-many via junction table.

Many-to-many:

```kotlin
object BookAuthors : Table() {
    val book = reference("book_id", Books)
    val author = reference("author_id", Authors)
    override val primaryKey = PrimaryKey(book, author)
}

class Book(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Book>(Books)
    var title by Books.title
    var authors by Author via BookAuthors
}
```

### Entity lifecycle

```kotlin
transaction {
    // Create
    val ada = Author.new { name = "Ada"; country = "GBR" }
    val book = Book.new {
        title = "Notes"
        author = ada
    }

    // Read
    val byId = Author.findById(1)              // Entity? (null if missing)
    val filtered = Author.find { Authors.country eq "USA" }.toList()
    val all = Author.all().toList()
    val lockedForUpdate = Author.find { Authors.id eq 1 }.forUpdate().first()

    // Update — just assign
    ada.country = "USA"

    // Delete
    ada.delete()

    // Refresh from DB
    ada.refresh(flush = true)

    // Force-write pending changes (rarely needed — flushed on commit)
    ada.flush()
}
```

### Eager loading — avoid N+1

```kotlin
// Single entity: preload a relationship
val book = Book.findById(1)?.load(Book::author, Book::authors)

// Collection: .with(...) preloads for every element
val list = Book.all().with(Book::author).toList()
```

Without these, each access to a lazy relationship fires its own query.

### Hooks

```kotlin
EntityHook.subscribe { action ->
    when (action.changeType) {
        EntityChangeType.Created -> logger.info { "created ${action.entityId}" }
        EntityChangeType.Updated -> ...
        EntityChangeType.Removed -> ...
    }
}
```

Hooks fire at flush, not at method call.

## Schema management

```kotlin
transaction {
    SchemaUtils.create(Cities, Users)                    // CREATE TABLE IF NOT EXISTS
    SchemaUtils.drop(Users, Cities)                      // DROP TABLE (order matters for FKs)
    SchemaUtils.createMissingTablesAndColumns(Users)     // add missing cols only
    SchemaUtils.statementsRequiredForDatabaseMigration(Users)
        .forEach { println(it) }                          // dry-run
}
```

For real migrations, use Flyway/Liquibase — `SchemaUtils` is suitable
for dev/test setup, not production evolution.

## Spring Boot

```kotlin
plugins { id("org.springframework.boot") }

dependencies {
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:<version>")
    implementation("org.jetbrains.exposed:exposed-dao:<version>")      // if using DAO
}
```

`application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: ${DB_PASSWORD}
exposed:
  generate-ddl: false
  show-sql: true
```

The starter wires a `SpringTransactionManager` — Spring's `@Transactional`
then works with Exposed code.

## Testing

### H2 in Postgres compatibility mode

```kotlin
class RepoTest : BehaviorSpec({
    beforeSpec {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        )
        transaction { SchemaUtils.create(Cities, Users) }
    }
    afterSpec {
        transaction { SchemaUtils.drop(Users, Cities) }
    }

    given("two adults") {
        transaction {
            Users.insert { it[email] = "a@x"; it[age] = 20 }
            Users.insert { it[email] = "b@x"; it[age] = 30 }
        }
        `when`("queried by age") {
            val count = transaction { Users.selectAll().where { Users.age greaterEq 18 }.count() }
            then("both returned") { count shouldBe 2L }
        }
    }
})
```

### Testcontainers for parity

```kotlin
val pg = PostgreSQLContainer("postgres:16").apply { start() }
Database.connect(pg.jdbcUrl, user = pg.username, password = pg.password)
```

H2 silently accepts some SQL that real PostgreSQL rejects — prefer
Testcontainers for any non-trivial code path.

## SQL functions

```kotlin
Users.select(
    Users.id.count(),
    Users.age.sum(),
    Users.age.avg(scale = 2),
    Users.age.min(),
    Users.age.max(),
    coalesce(Users.country, stringLiteral("??")),
    Users.email.lowerCase(),
    Users.email.trim(),
    Users.name.substring(1, 3),
)
```

Custom functions extend `Function<T>` and build SQL via
`queryBuilder { append("MY_FN(") ... append(")") }`.

## Anti-patterns

```kotlin
// WRONG — query outside transaction
Users.selectAll().toList()   // ExposedSQLException: no transaction

// WRONG — returning an Entity from a service
fun find(id: Int): Author? = transaction { Author.findById(id) }
// Accessing `author.books` after the transaction closes throws.
// RIGHT — map to a DTO inside the transaction.

// WRONG — blocking transaction in a suspend context
suspend fun get() = transaction { ... }                 // blocks coroutine thread
// RIGHT
suspend fun get() = newSuspendedTransaction { ... }

// WRONG — batchInsert defaults with a slow driver
Users.batchInsert(millionRows) { ... }
// RIGHT — disable generated-value return for speed
Users.batchInsert(millionRows, shouldReturnGeneratedValues = false) { ... }

// WRONG — `select { predicate }` (deprecated sugar)
Users.select { Users.age greater 18 }
// RIGHT
Users.selectAll().where { Users.age greater 18 }
Users.select(Users.id).where { Users.age greater 18 }

// WRONG — N+1 on a collection
Book.all().forEach { println(it.author.name) }           // 1 + N queries
// RIGHT
Book.all().with(Book::author).forEach { println(it.author.name) }

// WRONG — SchemaUtils as a migration tool in prod
SchemaUtils.createMissingTablesAndColumns(Users)         // Flyway/Liquibase instead

// WRONG — opening a Database per call
fun repo() { Database.connect(url, ...) ... }            // do this once at startup
```

## Pitfalls

| Symptom                                               | Cause / fix                                                            |
|-------------------------------------------------------|------------------------------------------------------------------------|
| `IllegalStateException: No transaction in context`    | Query ran outside `transaction { }` / `newSuspendedTransaction { }`.   |
| `LazyInitializationException`-style errors            | Touching an entity property after its transaction committed.           |
| `insertAndGetId` returns `EntityID`, not raw id       | Unwrap with `.value` if you need the primitive.                         |
| `timestamp` fails to compile                          | Missing `exposed-java-time` or `exposed-kotlin-datetime` dependency.   |
| H2 test passes, Postgres fails                        | H2's default mode is permissive; use `MODE=PostgreSQL` or Testcontainers. |
| FK cascade doesn't fire                               | Forgot `onDelete = ReferenceOption.CASCADE` on `.references(...)`.     |
| `selectAll().count()` is slow                         | Count on huge table. Push filters or use `count(*)` with conditions.   |
| `batchInsert` still slow                              | JDBC driver needs `rewriteBatchedInserts=true` (MySQL/Postgres).       |
| Entity changes not reflected                          | Pending writes buffered until flush/commit. Call `.flush()` or re-read. |
| Deadlocks under retry                                 | `maxAttempts` retries the whole block; ensure it's idempotent.         |
| `forUpdate` ignored                                   | Some DBs (e.g. H2 defaults) don't honor row locks. Test on the real DB.|
| Package `org.jetbrains.exposed.sql.*` not found       | On 1.x the root is `org.jetbrains.exposed.v1.core.*` etc. Align imports with the applied version. |

## Reference points

- Home — https://www.jetbrains.com/exposed/
- Docs — https://www.jetbrains.com/help/exposed/home.html
- GitHub — https://github.com/JetBrains/Exposed
- Wiki — https://github.com/JetBrains/Exposed/wiki
- Writerside topic source — https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/topics
- DSL CRUD — `DSL-CRUD-operations.topic`
- DSL Querying — `DSL-Querying-data.topic`
- DAO Entity — `DAO-Entity-definition.topic`
- DAO Relationships — `DAO-Relationships.topic`
- Transactions — `Transactions.md`
- Spring Boot — `Spring-Boot-integration.md`
- FAQ — `Frequently-Asked-Questions.md`
- Migration 1.0 — `Migration-Guide-1-0-0.md`
