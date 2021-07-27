## Introduction

Streamliner 5+ supports schema evolution. At the current time it only supports adding columns to tables but will support backwards compatible expansion of columns in the future.

This supports customers the following use case:

![Schema Evolution Diagram](../images/schema-evolution.png)

## Order of Operations

When performing schema evolution, careful ordering of operations is important to ensure that no data is lost in the schema evolution process.

For example, if a column is added and data is inserted into the new column before the downstream scripts are updated, those values will not be populated in Snowflake and an eventual reload will be required. In order to illustrate this point, assume we are loading CSV files to a table:

```sql
create or replace t1 (
C1 NUMBER(38,8),
C2 VARCHAR(300),
C3 VARCHAR(50)
);
```

If the source system defines a new column `C4` and starts populating before Streamliner is updated, Streamliner will run the following:

```sql
COPY INTO ...  (C1,C2,C3)
FROM ( SELECT $1::NUMBER(38, 8),
$2::VARCHAR(300),
$3::VARCHAR(50)
```

On this example CSV file:

```
1.0,r1c2,r1c3,r1c4
2.0,r2c2,r2c3,r2c4
3.0,r3c2,r3c3,r3c4
```

Which will complete without error, and the data in `C4` will be lost.

Conversely, if files exist in the stage without the new columns, referencing them in a `COPY INTO … SELECT` will not result in an error. For example, if we have the following statement for mapping a CSV file to a table:

```sql
COPY INTO ...  (C1,C2,C3,C4)
FROM ( SELECT $1::NUMBER(38, 8),
$2::VARCHAR(300),
$3::VARCHAR(50),
$4::VARCHAR(50)
```

And the following CSV file which does not have four fields:

```
1.0,r1c2,r1c3
1.0,r2c2,r2c3
1.0,r3c2,r3c3
```

The COPY INTO will complete successfully.

Therefore the correct order of operations is as follows:

1. Column added to source database.
2. Streamliner schema evolution is executed
   1. Adds columns to tables
   2. Redefines copy-into statements
3. Column in source system begins to be populated

Note that it's also important for a PIPE to be empty when it's redefined. Therefore, Streamliner takes care to PAUSE and then wait for it to empty before redefining it. You can see this in the create-snowpipe-schema-evolution template file.

## Commands

Let's say we have tables t1, t2 and t3 where t1 have column c1 and c2 in oracle and we want to get the metadata of these tables and generates scripts that
can be executed in Snowflake to create these tables. To achieve this below commands should be executed sequentially.

1. Schema command to extract oracle database schema.

```
./bin/streamliner schema --config conf/ingest-configuration.yml --output-file output/streamliner-configuration1.yml --database-password <db_pass>
```

Note : Before executing above command make sure output-file is a file that exists and should not be a directory.

Output of this command is streamliner-configuration1.yml that contains tables t1, t2, t3 and columns details of oracle schema.

2. Scripts command to generate initial scripts.

```
./bin/streamliner scripts --config output/streamliner-configuration1.yml --template-directory templates/snowflake --type-mapping conf/type-mapping.yml --output-path output/scripts
```

Output of step 1 streamliner-configuration1.yml is passed as config in this command.

Output of this command is scripts that can be executed in snowflake to create table t1, t2 and t3.

As time passes a new table t4 is created in oracle schema. Also a new column c3 is added in table t1. Now to keep snowflake updated with oracle schema we have to execute below commands.

3. Schema command to extract oracle schema and calculate difference.

```
./bin/streamliner schema --config conf/ingest-configuration.yml --output-file output/streamliner-configuration2.yml --previous-output-file output/streamliner-configuration1.yml --diff-output-file output/configDiff/streamliner-configDiff.yml --database-password <db_pass>
```

Note : Before executing above command make sure output-file, previous-output-file and diff-output-file is a file that exists and should not be a directory.

Output of step 1 streamliner-configuration1.yml is passed as previous-output-file in this command to calculate difference.

Output of this command is : 
   1. streamliner-configuration2.yml: This contains tables and columns details of oracle schema. This will also have details of new table t4 and column c3 in table t1.
   2. streamliner-configDiff.yml: This contains details of difference between oracle schema. In this case this will have table t4 and column c3 in table t1 details.

4. Scripts command to generate evolve schema scripts. 

```
./bin/streamliner scripts --config output/streamliner-configuration2.yml --config-diff output/configDiff/streamliner-configDiff.yml --template-directory templates/snowflake --type-mapping conf/type-mapping.yml --output-path output/evolve_schema_scripts
```

Output of step 3 streamliner-configuration2.yml is passed as config and streamliner-configDiff.yml as config-diff. 

Output of this command is scripts that can be executed in snowflake to create table 4 and alter table t1 to add column c3.

Note: In all the commands above any name can be provided to the configuration files.   
