/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.ballerinalang.compiler.bir.codegen.interop;

import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode;

/**
 * A wrapper used with JInterop to wrap BFuntions with JFunc description and data.
 *
 * @since 1.2.0
 */
public class BIRFunctionWrapper {

    public final PackageID packageID;
    public final BIRNode.BIRFunction func;
    public final String fullQualifiedClassName;
    public final String jvmMethodDescription;

    public BIRFunctionWrapper(PackageID packageID, BIRNode.BIRFunction func,
                              String fullQualifiedClassName, String jvmMethodDescription) {
        this.packageID = packageID;
        this.func = func;
        this.fullQualifiedClassName = fullQualifiedClassName;
        this.jvmMethodDescription = jvmMethodDescription;
    }
}
