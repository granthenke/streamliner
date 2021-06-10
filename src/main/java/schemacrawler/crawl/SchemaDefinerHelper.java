package schemacrawler.crawl;

import io.phdata.streamliner.schemadefiner.model.Configuration;
import io.phdata.streamliner.schemadefiner.model.FileFormat;
import schemacrawler.schema.Schema;
import schemacrawler.schema.Table;
import schemacrawler.schemacrawler.SchemaReference;

import java.util.*;

/* this classs is defined to help schemaDefiner to access schemacrawler package-private classes */
public class SchemaDefinerHelper {

    //  this method is added in this class because MutableTable, MutableColumn, MutablePrimaryKey classes are not public.
    // These are not accessible outside the package.
    public static StreamlinerCatalog mapTableDefToStreamlinerCatalog(Configuration configuration) {
        Schema schema = new SchemaReference(configuration.getSource().getSchema(), configuration.getSource().getSchema());
        Map<String, MutableTable> tables = new TreeMap<>();

        configuration.getTables().stream().forEach(tableDef -> {
            MutableTable table = new MutableTable(schema, tableDef.getSourceName());
            table.setRemarks(tableDef.getComment());
            tableDef.getColumns().stream().forEach(columnDef -> {
                MutableColumn column = new MutableColumn(table, columnDef.getSourceName());
                column.setColumnDataType(new MutableColumnDataType(schema, columnDef.getDataType()));
                column.setSize(columnDef.getPrecision());
                column.setDecimalDigits(columnDef.getScale());
                column.setRemarks(columnDef.getComment());
                table.addColumn(column);
                if (tableDef.getPrimaryKeys().contains(column.getName())) {
                    column.markAsPartOfPrimaryKey();
                }
            });
            tables.put(tableDef.getSourceName(), table);
        });
        Map<Schema, List<Table>> result = new HashMap<>();
        result.put(schema, new ArrayList<>(tables.values()));

        return new StreamlinerCatalog(configuration.getSource().getDriverClass(), Arrays.asList(schema),
                result);
    }

    public static StreamlinerCatalog mapAWSTableToCatalog(List<com.amazonaws.services.glue.model.Table> awsTables, String database , Map<Table, FileFormat> tableFileFormatMap) {
        Schema schema = new SchemaReference(database, database);
        Map<String, MutableTable> tables = new TreeMap<>();

        awsTables.stream().forEach(awsTable  -> {
            MutableTable table = new MutableTable(schema, awsTable.getName());
            table.setRemarks(awsTable.getDescription());
            awsTable.getStorageDescriptor().getColumns().stream().forEach(awsColumn ->{
                MutableColumn column = new MutableColumn(table, awsColumn.getName());
                column.setColumnDataType(new MutableColumnDataType(schema, awsColumn.getType()));
                column.setRemarks(awsColumn.getComment());
                table.addColumn(column);
            });
            // storing the FileFormat because it is needed to set FileFormat in TableDefinition in output configuration.
            // since retrieveSchema() does not provide detail of FileFormat, it is stored here.
            if(!tableFileFormatMap.containsKey(table)){
                tableFileFormatMap.put(table, new FileFormat(awsTable.getStorageDescriptor().getLocation(), awsTable.getStorageDescriptor().getOutputFormat()));
            }
            tables.put(awsTable.getName(), table);
        });
        Map<Schema, List<Table>> result = new HashMap<>();
        result.put(schema, new ArrayList<>(tables.values()));

        return new StreamlinerCatalog(null, Arrays.asList(schema),
                result);
    }
}
