/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.langlib.typedesc;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeId;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.runtime.internal.types.BArrayType;
import io.ballerina.runtime.internal.types.BErrorType;
import io.ballerina.runtime.internal.types.BObjectType;
import io.ballerina.runtime.internal.types.BTypeIdSet;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Extern function typedesc:typeIds.
 *
 * @since 2.0.0
 */
public class TypeIds {
    private static final String PACKAGE_NAME = "lang.typedesc";
    private static final String BALLERINA_BUILTIN_PKG_PREFIX = "ballerina";
    private static final String PACKAGE_VERSION = "1.0.0";
    private static final Module BALLERINA_TYPEDESC_PKG_ID =
            new Module(BALLERINA_BUILTIN_PKG_PREFIX, PACKAGE_NAME, PACKAGE_VERSION);

    private static final String TYPE_ID_TYPE_SIG = "record {| ModuleId moduleId; (string|int) localId; |}";
    private static final String MODULE_ID_TYPE_SIG =
            "record {| string organization; string name; string[] platformParts; |}";

    private static final ArrayType typeIdArrayType =
            new BArrayType(createTypeId(createModuleId(BALLERINA_TYPEDESC_PKG_ID), "empty").getType());

    public static Object typeIds(BTypedesc t, boolean primaryOnly) {
        Type describingType = t.getDescribingType();
        BTypeIdSet typeIdSet;
        switch (describingType.getTag()) {
            case TypeTags.ERROR_TAG:
                BErrorType errorType = (BErrorType) describingType;
                typeIdSet = errorType.typeIdSet;
                break;
            case TypeTags.OBJECT_TYPE_TAG:
                BObjectType objectType = (BObjectType) describingType;
                typeIdSet = objectType.typeIdSet;
                break;
            default:
                typeIdSet = new BTypeIdSet();
        }

        ArrayList<Object> objects = new ArrayList<>();
        for (TypeId id : typeIdSet.getIds()) {
            if (primaryOnly && !id.isPrimary()) {
                continue;
            }
            Object moduleId = createModuleId(id.getPkg());
            objects.add(createTypeId(moduleId, id.getName()));
        }

        BArray arrayValue = ValueCreator.createArrayValue(objects.toArray(new Object[]{}), typeIdArrayType);
        arrayValue.freezeDirect();
        return arrayValue;
    }

    private static BMap<BString, Object> createModuleId(Module mod) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("organization", StringUtils.fromString(mod.getOrg()));
        map.put("name", StringUtils.fromString(mod.getName()));
        map.put("platformParts",
                ValueCreator.createArrayValue(new BString[]{StringUtils.fromString(mod.getVersion())}));

        return ValueCreator.createReadonlyRecordValue(BALLERINA_TYPEDESC_PKG_ID, MODULE_ID_TYPE_SIG, map);
    }

    private static BMap<BString, Object> createTypeId(Object moduleId, String name) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("moduleId", moduleId);
        map.put("localId", StringUtils.fromString(name));

        return ValueCreator.createReadonlyRecordValue(BALLERINA_TYPEDESC_PKG_ID, TYPE_ID_TYPE_SIG, map);
    }
}
