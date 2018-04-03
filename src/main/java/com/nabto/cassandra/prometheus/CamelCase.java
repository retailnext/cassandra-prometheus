package com.nabto.cassandra.prometheus;

import java.util.regex.Pattern;

class CamelCase {
    private static Pattern camelCasePattern = Pattern.compile("(?!^)(?<!_)([A-Z])");
    private static Pattern fixCASPattern = Pattern.compile("CAS");
    private static Pattern fixCQLPattern = Pattern.compile("CQL");
    private static Pattern fixSSTablePattern = Pattern.compile("SSTable");

    static String toSnakeCase(String name) {
        // Prevent names like s_s_table_...
        name = fixCASPattern.matcher(name).replaceAll("Cas");
        name = fixCQLPattern.matcher(name).replaceAll("Cql");
        name = fixSSTablePattern.matcher(name).replaceAll("Sstable");
        return camelCasePattern.matcher(name).replaceAll("_$1").toLowerCase();
    }
}
