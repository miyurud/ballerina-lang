/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.model.types;

import org.ballerinalang.model.util.Flags;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.codegen.RecordTypeInfo;
import org.ballerinalang.util.codegen.TypeInfo;

/**
 * {@code BRecordType} represents a user defined record type in Ballerina.
 *
 * @since 0.971.0
 */
public class BRecordType extends BStructureType {

    public RecordTypeInfo recordTypeInfo;

    public boolean sealed;
    public BType restFieldType;

    private BMap<String, BValue> implicitInitValue;

    /**
     * Create a {@code BStructType} which represents the user defined struct type.
     *
     * @param recordTypeInfo record type info object
     * @param typeName string name of the type
     * @param pkgPath  package of the struct
     * @param flags of the record type
     */
    public BRecordType(RecordTypeInfo recordTypeInfo, String typeName, String pkgPath, int flags) {
        super(typeName, pkgPath, flags, BMap.class);
        this.recordTypeInfo = recordTypeInfo;
    }

    public TypeInfo getTypeInfo() {
        return recordTypeInfo;
    }

    @Override
    public <V extends BValue> V getZeroValue() {
        if (this.implicitInitValue == null) {
            this.implicitInitValue = new BMap<>(this);
            this.fields.entrySet().stream()
                    .filter(entry -> !Flags.isFlagOn(entry.getValue().flags, Flags.OPTIONAL))
                    .forEach(entry -> {
                        BValue value = entry.getValue().fieldType.getZeroValue();
                        implicitInitValue.put(entry.getKey(), value);
                    });
        }

        try {
            return (V) implicitInitValue.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Failed to get implicit initial value", e);
        }
    }

    @Override
    public <V extends BValue> V getEmptyValue() {
        if (this.implicitInitValue == null) {
            this.implicitInitValue = new BMap<>(this);
            this.fields.entrySet().stream()
                    .filter(entry -> !Flags.isFlagOn(entry.getValue().flags, Flags.OPTIONAL))
                    .forEach(entry -> {
                        BValue value = entry.getValue().fieldType.getEmptyValue();
                        implicitInitValue.put(entry.getKey(), value);
                    });
        }

        try {
            return (V) implicitInitValue.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Failed to get empty value", e);
        }
    }

    @Override
    public int getTag() {
        return TypeTags.RECORD_TYPE_TAG;
    }
}

