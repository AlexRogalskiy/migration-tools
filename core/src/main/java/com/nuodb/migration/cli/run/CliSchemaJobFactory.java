/**
 * Copyright (c) 2012, NuoDB, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of NuoDB, Inc. nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NUODB, INC. BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nuodb.migration.cli.run;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.nuodb.migration.cli.CliResources;
import com.nuodb.migration.cli.parse.*;
import com.nuodb.migration.cli.parse.option.GroupBuilder;
import com.nuodb.migration.cli.parse.option.OptionFormat;
import com.nuodb.migration.cli.parse.option.OptionToolkit;
import com.nuodb.migration.jdbc.JdbcConstants;
import com.nuodb.migration.jdbc.dialect.IdentifierNormalizer;
import com.nuodb.migration.jdbc.dialect.IdentifierNormalizers;
import com.nuodb.migration.jdbc.dialect.IdentifierQuoting;
import com.nuodb.migration.jdbc.dialect.IdentifierQuotings;
import com.nuodb.migration.jdbc.metadata.MetaDataType;
import com.nuodb.migration.jdbc.metadata.generator.GroupScriptsBy;
import com.nuodb.migration.jdbc.metadata.generator.ScriptType;
import com.nuodb.migration.schema.SchemaJobFactory;
import com.nuodb.migration.spec.*;
import com.nuodb.migration.utils.ReflectionUtils;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Multimaps.newListMultimap;
import static com.google.common.collect.Sets.newHashSet;
import static com.nuodb.migration.jdbc.metadata.generator.ScriptType.valueOf;
import static com.nuodb.migration.utils.Priority.LOW;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.apache.commons.lang3.StringUtils.replace;

/**
 * @author Sergey Bushik
 */
public class CliSchemaJobFactory extends CliRunSupport implements CliRunFactory, CliResources {

    public static final String IDENTIFIER_QUOTING_MINIMAL = "minimal";
    public static final String IDENTIFIER_QUOTING_ALWAYS = "always";

    public static final String IDENTIFIER_NORMALIZER_NOOP = "noop";
    public static final String IDENTIFIER_NORMALIZER_STANDARD = "standard";
    public static final String IDENTIFIER_NORMALIZER_LOWERCASE = "lowercase";
    public static final String IDENTIFIER_NORMALIZER_UPPERCASE = "uppercase";

    private static final String COMMAND = "schema";

    public CliSchemaJobFactory(OptionToolkit optionToolkit) {
        super(optionToolkit);
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public CliRun createCliRun() {
        return new CliSchemaJob();
    }

    class JdbcTypeSpecValuesCollector implements OptionProcessor {

        private ListMultimap<String, Object> lastVisitedValues = newListMultimap(
                Maps.<String, Collection<Object>>newLinkedHashMap(),
                new Supplier<List<Object>>() {
                    @Override
                    public List<Object> get() {
                        return newArrayList();
                    }
                });
        private ListMultimap<String, Object> values = newListMultimap(
                Maps.<String, Collection<Object>>newLinkedHashMap(),
                new Supplier<List<Object>>() {
                    @Override
                    public List<Object> get() {
                        return newArrayList();
                    }
                });
        private int count;

        @Override
        public void preProcess(CommandLine commandLine, Option option, ListIterator<String> arguments) {
            pad(commandLine);
            count++;
        }

        private void pad(CommandLine commandLine) {
            pad(commandLine, JDBC_TYPE_NAME_OPTION, count);
            pad(commandLine, JDBC_TYPE_CODE_OPTION, count);
            pad(commandLine, JDBC_TYPE_SIZE_OPTION, count);
            pad(commandLine, JDBC_TYPE_PRECISION_OPTION, count);
            pad(commandLine, JDBC_TYPE_SCALE_OPTION, count);
        }

        private void pad(CommandLine commandLine, String option, int count) {
            List<String> optionValues = newArrayList(commandLine.<String>getValues(option));
            List<Object> lastVisitedOptionValues = lastVisitedValues.replaceValues(option, optionValues);
            optionValues.removeAll(lastVisitedOptionValues);

            List<Object> paddedOptionValues = values.get(option);
            paddedOptionValues.addAll(optionValues);
            for (int i = paddedOptionValues.size(); i < count; i++) {
                paddedOptionValues.add(i, null);
            }
        }

        @Override
        public void process(CommandLine commandLine, Option option, ListIterator<String> arguments) {
        }

        @Override
        public void postProcess(CommandLine commandLine, Option option) {
            pad(commandLine);
        }

        public ListMultimap<String, Object> getValues() {
            return values;
        }
    }

    class CliSchemaJob extends CliRunJob {

        private JdbcTypeSpecValuesCollector jdbcTypeSpecValuesCollector = new JdbcTypeSpecValuesCollector();

        public CliSchemaJob() {
            super(CliSchemaJobFactory.this.getOptionFormat(), COMMAND, new SchemaJobFactory());
        }

        @Override
        protected Option createOption() {
            GroupBuilder group = newGroupBuilder().withName(getResources().getMessage(SCHEMA_GROUP_NAME));
            group.withRequired(true);
            group.withOption(createSourceGroup());
            group.withOption(createTargetGroup());
            group.withOption(createOutputGroup());
            createSchemaOptions(group);
            return group.build();
        }

        @Override
        protected void bind(OptionSet optionSet) {
            SchemaSpec schemaSpec = new SchemaSpec();
            schemaSpec.setSourceConnectionSpec(parseSourceGroup(optionSet, this));
            schemaSpec.setTargetConnectionSpec(parseTargetGroup(optionSet, this));
            schemaSpec.setOutputSpec(parseOutputGroup(optionSet, this));
            parseSchemaOptions(schemaSpec, optionSet, this);
            ((SchemaJobFactory) getJobFactory()).setSchemaSpec(schemaSpec);
        }

        protected void createSchemaOptions(GroupBuilder group) {
            GroupBuilder typeGroup = newGroupBuilder().withName(getResources().getMessage(JDBC_TYPE_GROUP_NAME));
            Option typeName = newBasicOptionBuilder().
                    withName(JDBC_TYPE_NAME_OPTION).
                    withDescription(getMessage(JDBC_TYPE_NAME_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(JDBC_TYPE_NAME_ARGUMENT_NAME)).
                                    withHelpValues(singleton(getMessage(JDBC_TYPE_NAME_ARGUMENT_NAME))).
                                    withOptionProcessor(jdbcTypeSpecValuesCollector).
                                    withMinimum(1).withMaximum(Integer.MAX_VALUE).withRequired(true).build()
                    ).build();
            typeGroup.withOption(typeName);
            Option typeCode = newBasicOptionBuilder().
                    withName(JDBC_TYPE_CODE_OPTION).
                    withDescription(getMessage(JDBC_TYPE_CODE_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(JDBC_TYPE_CODE_ARGUMENT_NAME)).
                                    withHelpValues(singleton(getMessage(JDBC_TYPE_CODE_ARGUMENT_NAME))).
                                    withMinimum(1).withMaximum(Integer.MAX_VALUE).withRequired(true).build()
                    ).build();
            typeGroup.withOption(typeCode);
            Option typeSize = newBasicOptionBuilder().
                    withName(JDBC_TYPE_SIZE_OPTION).
                    withDescription(getMessage(JDBC_TYPE_SIZE_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(JDBC_TYPE_SIZE_ARGUMENT_NAME)).
                                    withHelpValues(singleton(getMessage(JDBC_TYPE_SIZE_ARGUMENT_NAME))).
                                    withMaximum(Integer.MAX_VALUE).build()
                    ).build();
            typeGroup.withOption(typeSize);
            Option typePrecision = newBasicOptionBuilder().
                    withName(JDBC_TYPE_PRECISION_OPTION).
                    withDescription(getMessage(JDBC_TYPE_PRECISION_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(JDBC_TYPE_PRECISION_ARGUMENT_NAME)).
                                    withHelpValues(singleton(getMessage(JDBC_TYPE_PRECISION_ARGUMENT_NAME))).
                                    withMaximum(Integer.MAX_VALUE).build()
                    ).build();
            typeGroup.withOption(typePrecision);
            Option typeScale = newBasicOptionBuilder().
                    withName(JDBC_TYPE_SCALE_OPTION).
                    withDescription(getMessage(JDBC_TYPE_SCALE_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(JDBC_TYPE_SCALE_ARGUMENT_NAME)).
                                    withHelpValues(singleton(getMessage(JDBC_TYPE_SCALE_ARGUMENT_NAME))).
                                    withMaximum(Integer.MAX_VALUE).build()
                    ).build();
            typeGroup.withOption(typeScale);
            typeGroup.withMaximum(Integer.MAX_VALUE);
            group.withOption(typeGroup.build());

            OptionFormat optionFormat = new OptionFormat(getOptionFormat());
            optionFormat.setValuesSeparator(null);

            Option generate = newRegexOptionBuilder().
                    withName(SCHEMA_META_DATA_OPTION).
                    withDescription(getMessage(SCHEMA_META_DATA_OPTION_DESCRIPTION)).
                    withRegex(SCHEMA_META_DATA_OPTION, 1, LOW).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(SCHEMA_META_DATA_ARGUMENT_NAME)).
                                    withOptionFormat(optionFormat).withMinimum(1).withMaximum(Integer.MAX_VALUE).build()
                    )
                    .build();
            group.withOption(generate);

            Collection<String> scriptTypeHelpValues = Lists.transform(asList(ScriptType.values()),
                    new Function<ScriptType, String>() {
                        @Override
                        public String apply(ScriptType scriptType) {
                            return scriptType.name().toLowerCase();
                        }
                    });
            Option scriptType = newBasicOptionBuilder().
                    withName(SCHEMA_SCRIPT_TYPE_OPTION).
                    withDescription(getMessage(SCHEMA_SCRIPT_TYPE_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(SCHEMA_SCRIPT_TYPE_ARGUMENT_NAME)).
                                    withMinimum(1).
                                    withMaximum(ScriptType.values().length).
                                    withHelpValues(scriptTypeHelpValues).build()
                    ).build();
            group.withOption(scriptType);

            Option groupScriptsBy = newBasicOptionBuilder().
                    withName(SCHEMA_GROUP_SCRIPTS_BY_OPTION).
                    withDescription(getMessage(SCHEMA_GROUP_SCRIPTS_BY_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(SCHEMA_GROUP_SCRIPTS_BY_ARGUMENT_NAME)).build()
                    ).build();
            group.withOption(groupScriptsBy);

            Option identifierQuoting = newBasicOptionBuilder().
                    withName(SCHEMA_IDENTIFIER_QUOTING).
                    withDescription(getMessage(SCHEMA_IDENTIFIER_QUOTING_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(SCHEMA_IDENTIFIER_QUOTING_ARGUMENT_NAME)).build()
                    ).build();
            group.withOption(identifierQuoting);

            Option identifierNormalizer = newBasicOptionBuilder().
                    withName(SCHEMA_IDENTIFIER_NORMALIZER).
                    withDescription(getMessage(SCHEMA_IDENTIFIER_NORMALIZER_OPTION_DESCRIPTION)).
                    withArgument(
                            newArgumentBuilder().
                                    withName(getMessage(SCHEMA_IDENTIFIER_NORMALIZER_ARGUMENT_NAME)).build()
                    ).build();
            group.withOption(identifierNormalizer);
        }

        protected ConnectionSpec parseTargetGroup(OptionSet optionSet, Option option) {
            JdbcConnectionSpec connection = new JdbcConnectionSpec();
            connection.setDriverClassName(JdbcConstants.NUODB_DRIVER_CLASS_NAME);
            connection.setUrl((String) optionSet.getValue(TARGET_URL_OPTION));
            connection.setUsername((String) optionSet.getValue(TARGET_USERNAME_OPTION));
            connection.setPassword((String) optionSet.getValue(TARGET_PASSWORD_OPTION));
            connection.setSchema((String) optionSet.getValue(TARGET_SCHEMA_OPTION));
            connection.setProperties(parseProperties(optionSet, TARGET_PROPERTIES_OPTION, option));
            return connection;
        }

        protected void parseSchemaOptions(SchemaSpec schemaSpec, OptionSet optionSet, Option option) {
            if (optionSet.hasOption(SCHEMA_META_DATA_OPTION)) {
                Collection<String> values = optionSet.getValues(SCHEMA_META_DATA_OPTION);
                Set<MetaDataType> metaDataTypes = newHashSet(MetaDataType.TYPES);
                for (Iterator<String> iterator = values.iterator(); iterator.hasNext(); ) {
                    MetaDataType metaDataType = MetaDataType.NAME_TYPE_MAP.get(replace(iterator.next(), ".", "_"));
                    String booleanValue = iterator.next();
                    if (booleanValue == null || parseBoolean(booleanValue)) {
                        metaDataTypes.add(metaDataType);
                    } else {
                        metaDataTypes.remove(metaDataType);
                    }
                }
                schemaSpec.setMetaDataTypes(metaDataTypes);
            }
            List<String> scriptTypeValues = optionSet.getValues(SCHEMA_SCRIPT_TYPE_OPTION);
            Collection<ScriptType> scriptTypes = newHashSet();
            for (String scriptTypeValue : scriptTypeValues) {
                scriptTypes.add(valueOf(scriptTypeValue.toUpperCase()));
            }
            if (scriptTypes.isEmpty()) {
                scriptTypes = newHashSet(ScriptType.values());
            }
            schemaSpec.setScriptTypes(scriptTypes);

            Map<String, GroupScriptsBy> groupScriptsByConditionMap = Maps.newHashMap();
            for (GroupScriptsBy groupScriptsBy : GroupScriptsBy.values()) {
                groupScriptsByConditionMap.put(groupScriptsBy.getCondition(), groupScriptsBy);
            }
            String groupScriptsByCondition = (String) optionSet.getValue(SCHEMA_GROUP_SCRIPTS_BY_OPTION);
            GroupScriptsBy groupScriptsBy;
            if (groupScriptsByCondition != null) {
                groupScriptsBy = groupScriptsByConditionMap.get(groupScriptsByCondition);
                if (groupScriptsBy == null) {
                    throw new OptionException(option,
                            format("Unexpected value for %s option, valid values are %s",
                                    SCHEMA_GROUP_SCRIPTS_BY_OPTION, groupScriptsByConditionMap.keySet()));
                }
            } else {
                groupScriptsBy = GroupScriptsBy.TABLE;
            }
            schemaSpec.setGroupScriptsBy(groupScriptsBy);

            String identifierQuotingValue = (String) optionSet.getValue(SCHEMA_IDENTIFIER_QUOTING);
            IdentifierQuoting identifierQuoting = null;
            if (identifierQuotingValue != null) {
                identifierQuoting = getIdentifierQuotings().get(identifierQuotingValue);
                if (identifierQuoting == null) {
                    Class<IdentifierQuoting> identifierQuotingClass = ReflectionUtils.loadClass(identifierQuotingValue);
                    identifierQuoting = ReflectionUtils.newInstance(identifierQuotingClass);
                }
            }
            schemaSpec.setIdentifierQuoting(
                    identifierQuoting != null ? identifierQuoting : IdentifierQuotings.always());

            String identifierNormalizerValue = (String) optionSet.getValue(SCHEMA_IDENTIFIER_NORMALIZER);
            IdentifierNormalizer identifierNormalizer = null;
            if (identifierNormalizerValue != null) {
                identifierNormalizer = getIdentifierNormalizers().get(identifierNormalizerValue);
                if (identifierNormalizer == null) {
                    Class<IdentifierNormalizer> identifierNormalizerClass =
                            ReflectionUtils.loadClass(identifierNormalizerValue);
                    identifierNormalizer =
                            ReflectionUtils.newInstance(identifierNormalizerClass);
                }
            }
            schemaSpec.setIdentifierNormalizer(
                    identifierNormalizer != null ? identifierNormalizer : IdentifierNormalizers.noop());

            ListMultimap<String, Object> values = jdbcTypeSpecValuesCollector.getValues();
            int count = values.get(JDBC_TYPE_NAME_OPTION).size();
            Collection<JdbcTypeSpec> jdbcTypeSpecs = Lists.newArrayList();
            for (int i = 0; i < count; i++) {
                String typeName = (String) values.get(JDBC_TYPE_NAME_OPTION).get(i);
                String typeCode = (String) values.get(JDBC_TYPE_CODE_OPTION).get(i);
                String size = (String) values.get(JDBC_TYPE_SIZE_OPTION).get(i);
                String precision = (String) values.get(JDBC_TYPE_PRECISION_OPTION).get(i);
                String scale = (String) values.get(JDBC_TYPE_SCALE_OPTION).get(i);
                JdbcTypeSpec jdbcTypeSpec = new JdbcTypeSpec();
                jdbcTypeSpec.setTypeName(typeName);
                jdbcTypeSpec.setTypeCode(Integer.parseInt(typeCode));
                jdbcTypeSpec.setTypeCode(Integer.parseInt(typeCode));
                jdbcTypeSpec.setSize(size != null ? Integer.parseInt(size) : null);
                jdbcTypeSpec.setPrecision(precision != null ? Integer.parseInt(precision) : null);
                jdbcTypeSpec.setScale(scale != null ? Integer.parseInt(scale) : null);
                jdbcTypeSpecs.add(jdbcTypeSpec);
            }
            schemaSpec.setJdbcTypeSpecs(jdbcTypeSpecs);
        }
    }

    protected Map<String, IdentifierNormalizer> getIdentifierNormalizers() {
        Map<String, IdentifierNormalizer> identifierNormalizers =
                new TreeMap<String, IdentifierNormalizer>(CASE_INSENSITIVE_ORDER);
        identifierNormalizers.put(IDENTIFIER_NORMALIZER_NOOP, IdentifierNormalizers.noop());
        identifierNormalizers.put(IDENTIFIER_NORMALIZER_STANDARD, IdentifierNormalizers.standard());
        identifierNormalizers.put(IDENTIFIER_NORMALIZER_LOWERCASE, IdentifierNormalizers.lowerCase());
        identifierNormalizers.put(IDENTIFIER_NORMALIZER_UPPERCASE, IdentifierNormalizers.upperCase());
        return identifierNormalizers;
    }

    protected Map<String, IdentifierQuoting> getIdentifierQuotings() {
        Map<String, IdentifierQuoting> identifierQuotings =
                new TreeMap<String, IdentifierQuoting>(CASE_INSENSITIVE_ORDER);
        identifierQuotings.put(IDENTIFIER_QUOTING_MINIMAL, IdentifierQuotings.minimal());
        identifierQuotings.put(IDENTIFIER_QUOTING_ALWAYS, IdentifierQuotings.always());
        return identifierQuotings;
    }

    @Override
    protected Group createOutputGroup() {
        GroupBuilder group = newGroupBuilder().withName(getMessage(SCHEMA_OUTPUT_GROUP_NAME));
        Option path = newBasicOptionBuilder().
                withName(OUTPUT_PATH_OPTION).
                withRequired(true).
                withDescription(getMessage(OUTPUT_PATH_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(OUTPUT_PATH_ARGUMENT_NAME)).
                                withMinimum(1).
                                withRequired(true).build()
                ).build();
        group.withOption(path);
        return group.build();
    }

    @Override
    protected ResourceSpec parseOutputGroup(OptionSet optionSet, Option option) {
        ResourceSpec resource = null;
        if (optionSet.hasOption(OUTPUT_PATH_OPTION)) {
            resource = new ResourceSpec();
            resource.setPath((String) optionSet.getValue(OUTPUT_PATH_OPTION));
        }
        return resource;
    }

    @Override
    protected Group createTargetGroup() {
        GroupBuilder group = newGroupBuilder().withName(getMessage(TARGET_GROUP_NAME));

        Option url = newBasicOptionBuilder().
                withName(TARGET_URL_OPTION).
                withDescription(getMessage(TARGET_URL_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(TARGET_URL_ARGUMENT_NAME)).
                                withMinimum(1).withRequired(true).build()
                ).build();
        group.withOption(url);

        Option username = newBasicOptionBuilder().
                withName(TARGET_USERNAME_OPTION).
                withDescription(getMessage(TARGET_USERNAME_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(TARGET_USERNAME_ARGUMENT_NAME)).build()
                ).build();
        group.withOption(username);

        Option password = newBasicOptionBuilder().
                withName(TARGET_PASSWORD_OPTION).
                withDescription(getMessage(TARGET_PASSWORD_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(TARGET_PASSWORD_ARGUMENT_NAME)).build()
                ).build();
        group.withOption(password);

        Option properties = newBasicOptionBuilder().
                withName(TARGET_PROPERTIES_OPTION).
                withDescription(getMessage(TARGET_PROPERTIES_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(TARGET_PROPERTIES_ARGUMENT_NAME)).build()
                ).build();
        group.withOption(properties);

        Option schema = newBasicOptionBuilder().
                withName(TARGET_SCHEMA_OPTION).
                withDescription(getMessage(TARGET_SCHEMA_OPTION_DESCRIPTION)).
                withArgument(
                        newArgumentBuilder().
                                withName(getMessage(TARGET_SCHEMA_ARGUMENT_NAME)).build()
                ).build();
        group.withOption(schema);
        return group.build();
    }
}

