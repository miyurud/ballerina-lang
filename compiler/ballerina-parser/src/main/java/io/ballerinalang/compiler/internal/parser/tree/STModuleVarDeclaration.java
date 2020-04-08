/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerinalang.compiler.internal.parser.tree;

import io.ballerinalang.compiler.syntax.tree.VariableDeclaration;
import io.ballerinalang.compiler.syntax.tree.Node;
import io.ballerinalang.compiler.syntax.tree.NonTerminalNode;

public class STModuleVarDeclaration extends STStatement {

    public final STNode annots;
    public final STNode finalKeyword;
    public final STNode typeName;
    public final STNode variableName;
    public final STNode equalsToken;
    public final STNode initializer;
    public final STNode semicolonToken;

    STModuleVarDeclaration(STNode metadata,
                           STNode finalKeyword,
                           STNode typeName,
                           STNode variableName,
                           STNode equalsToken,
                           STNode initializer,
                           STNode semicolonToken) {
        super(SyntaxKind.MODULE_VAR_DECL);
        this.annots = metadata;
        this.finalKeyword = finalKeyword;
        this.typeName = typeName;
        this.variableName = variableName;
        this.equalsToken = equalsToken;
        this.initializer = initializer;
        this.semicolonToken = semicolonToken;

        addChildren(metadata, finalKeyword, typeName, variableName, equalsToken, initializer, semicolonToken);
    }

    @Override
    public Node createFacade(int position, NonTerminalNode parent) {
        return new VariableDeclaration(this, position, parent);
    }
}
