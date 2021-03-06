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

package org.wso2.ballerinalang.compiler.bir.codegen;

import io.ballerina.runtime.api.utils.IdentifierUtils;
import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.bir.codegen.methodgen.InitMethodGen;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRBasicBlock;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRFunction;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRFunctionParameter;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRTypeDefinition;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode.BIRVariableDcl;
import org.wso2.ballerinalang.compiler.bir.model.BIRNonTerminator.UnaryOP;
import org.wso2.ballerinalang.compiler.bir.model.BIROperand;
import org.wso2.ballerinalang.compiler.bir.model.BIRTerminator.Branch;
import org.wso2.ballerinalang.compiler.bir.model.BIRTerminator.GOTO;
import org.wso2.ballerinalang.compiler.bir.model.InstructionKind;
import org.wso2.ballerinalang.compiler.bir.model.VarKind;
import org.wso2.ballerinalang.compiler.bir.model.VarScope;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.util.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.wso2.ballerinalang.compiler.bir.codegen.JvmCodeGenUtil.toNameString;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.DESUGARED_BB_ID_NAME;

/**
 * BIR desugar phase related methods at JVM code generation.
 *
 * @since 1.2.0
 */
public class JvmDesugarPhase {

    public static void addDefaultableBooleanVarsToSignature(BIRFunction func, BType booleanType) {

        func.type = new BInvokableType(func.type.paramTypes, func.type.restType,
                                       func.type.retType, func.type.tsymbol);
        BInvokableType type = func.type;
        func.type.paramTypes = updateParamTypesWithDefaultableBooleanVar(func.type.paramTypes,
                                                                         type.restType, booleanType);
        int index = 0;
        List<BIRVariableDcl> updatedVars = new ArrayList<>();
        List<BIRVariableDcl> localVars = func.localVars;
        int nameIndex = 0;

        for (BIRVariableDcl localVar : localVars) {
            updatedVars.add(index, localVar);
            index += 1;

            if (!(localVar instanceof BIRFunctionParameter)) {
                continue;
            }

            // An additional boolean arg is gen for each function parameter.
            String argName = "%syn" + nameIndex;
            nameIndex += 1;
            BIRFunctionParameter booleanVar = new BIRFunctionParameter(null, booleanType,
                    new Name(argName), VarScope.FUNCTION, VarKind.ARG, "", false);
            updatedVars.add(index, booleanVar);
            index += 1;
        }
        func.localVars = updatedVars;
    }

    public static void enrichWithDefaultableParamInits(BIRFunction currentFunc, InitMethodGen initMethodGen) {
        int k = 1;
        List<BIRFunctionParameter> functionParams = new ArrayList<>();
        List<BIRVariableDcl> localVars = currentFunc.localVars;
        while (k < localVars.size()) {
            BIRVariableDcl localVar = localVars.get(k);
            if (localVar instanceof BIRFunctionParameter) {
                functionParams.add((BIRFunctionParameter) localVar);
            }
            k += 1;
        }

        initMethodGen.resetIds();

        List<BIRBasicBlock> basicBlocks = new ArrayList<>();

        BIRBasicBlock nextBB = insertAndGetNextBasicBlock(basicBlocks, DESUGARED_BB_ID_NAME, initMethodGen);

        int paramCounter = 0;
        Location pos = currentFunc.pos;
        while (paramCounter < functionParams.size()) {
            BIRFunctionParameter funcParam = functionParams.get(paramCounter);
            if (funcParam != null && funcParam.hasDefaultExpr) {
                int boolParam = paramCounter + 1;
                BIRFunctionParameter funcBooleanParam = getFunctionParam(functionParams.get(boolParam));
                BIROperand boolRef = new BIROperand(funcBooleanParam);
                UnaryOP notOp = new UnaryOP(pos, InstructionKind.NOT, boolRef, boolRef);
                nextBB.instructions.add(notOp);
                List<BIRBasicBlock> bbArray = currentFunc.parameters.get(funcParam);
                BIRBasicBlock trueBB = bbArray.get(0);
                basicBlocks.addAll(bbArray);
                BIRBasicBlock falseBB = insertAndGetNextBasicBlock(basicBlocks, DESUGARED_BB_ID_NAME, initMethodGen);
                nextBB.terminator = new Branch(pos, boolRef, trueBB, falseBB);

                BIRBasicBlock lastBB = bbArray.get(bbArray.size() - 1);
                lastBB.terminator = new GOTO(pos, falseBB);

                nextBB = falseBB;
            }
            paramCounter += 2;
        }

        if (basicBlocks.size() == 1) {
            // this means only one block added, if there are default vars, there must be more than one block
            return;
        }
        if (currentFunc.basicBlocks.size() == 0) {
            currentFunc.basicBlocks = basicBlocks;
            return;
        }

        BIRBasicBlock firstBB = currentFunc.basicBlocks.get(0);

        nextBB.terminator = new GOTO(pos, firstBB);
        basicBlocks.addAll(currentFunc.basicBlocks);

        currentFunc.basicBlocks = basicBlocks;
    }

    public static BIRBasicBlock insertAndGetNextBasicBlock(List<BIRBasicBlock> basicBlocks,
                                                           String prefix, InitMethodGen initMethodGen) {
        BIRBasicBlock nextbb = new BIRBasicBlock(getNextDesugarBBId(prefix, initMethodGen));
        basicBlocks.add(nextbb);
        return nextbb;
    }

    public static Name getNextDesugarBBId(String prefix, InitMethodGen initMethodGen) {
        int nextId = initMethodGen.incrementAndGetNextId();
        return new Name(prefix + nextId);
    }

    private static List<BType> updateParamTypesWithDefaultableBooleanVar(List<BType> funcParams, BType restType,
                                                                         BType booleanType) {

        List<BType> paramTypes = new ArrayList<>();

        int counter = 0;
        int index = 0;
        // Update the param types to add boolean variables to indicate if the previous variable contains a user
        // given value
        int size = funcParams == null ? 0 : funcParams.size();
        while (counter < size) {
            paramTypes.add(index, funcParams.get(counter));
            paramTypes.add(index + 1, booleanType);
            index += 2;
            counter += 1;
        }
        if (restType != null) {
            paramTypes.add(index, restType);
            paramTypes.add(index + 1, booleanType);
        }
        return paramTypes;
    }

    static void rewriteRecordInits(List<BIRTypeDefinition> typeDefs) {

        for (BIRTypeDefinition typeDef : typeDefs) {
            BType recordType = typeDef.type;
            if (recordType.tag != TypeTags.RECORD) {
                continue;
            }
            List<BIRFunction> attachFuncs = typeDef.attachedFuncs;
            for (BIRFunction func : attachFuncs) {
                rewriteRecordInitFunction(func, (BRecordType) recordType);
            }
        }
    }

    private static void rewriteRecordInitFunction(BIRFunction func, BRecordType recordType) {

        BIRVariableDcl receiver = func.receiver;

        // Rename the function name by appending the record name to it.
        // This done to avoid frame class name overlapping.
        func.name = new Name(toNameString(recordType) + func.name.value);

        // change the kind of receiver to 'ARG'
        receiver.kind = VarKind.ARG;

        // Update the name of the reciever. Then any instruction that was refering to the receiver will
        // now refer to the injected parameter.
        String paramName = "$_" + receiver.name.value;
        receiver.name = new Name(paramName);

        // Inject an additional parameter to accept the self-record value into the init function
        BIRFunctionParameter selfParam = new BIRFunctionParameter(null, receiver.type, receiver.name,
                                                                  receiver.scope, VarKind.ARG, paramName, false);

        List<BType> updatedParamTypes = Lists.of(receiver.type);
        updatedParamTypes.addAll(func.type.paramTypes);
        func.type = new BInvokableType(updatedParamTypes, func.type.restType, func.type.retType, null);

        List<BIRVariableDcl> localVars = func.localVars;
        List<BIRVariableDcl> updatedLocalVars = new ArrayList<>();
        updatedLocalVars.add(localVars.get(0));
        updatedLocalVars.add(selfParam);
        int index = 1;
        while (index < localVars.size()) {
            updatedLocalVars.add(localVars.get(index));
            index += 1;
        }
        func.localVars = updatedLocalVars;
    }

    private static BIRFunctionParameter getFunctionParam(BIRFunctionParameter localVar) {
        if (localVar == null) {
            throw new BLangCompilerException("Invalid function parameter");
        }

        return localVar;
    }

    private JvmDesugarPhase() {
    }

    static HashMap<String, String> encodeModuleIdentifiers(BIRNode.BIRPackage module, Names names) {
        HashMap<String, String> originalIdentifierMap = new HashMap<>();
        encodePackageIdentifiers(module.packageID, names, originalIdentifierMap);
        encodeGlobalVariableIdentifiers(module.globalVars, names, originalIdentifierMap);
        encodeFunctionIdentifiers(module.functions, names, originalIdentifierMap);
        encodeTypeDefIdentifiers(module.typeDefs, names, originalIdentifierMap);
        return originalIdentifierMap;
    }

    private static void encodePackageIdentifiers(PackageID packageID, Names names,
                                                 HashMap<String, String> originalIdentifierMap) {
        packageID.orgName = names.fromString(encodeNonFunctionIdentifier(packageID.orgName.value,
                                                                         originalIdentifierMap));
        packageID.name = names.fromString(encodeNonFunctionIdentifier(packageID.name.value, originalIdentifierMap));
    }

    private static void encodeTypeDefIdentifiers(List<BIRTypeDefinition> typeDefs, Names names,
                                                 HashMap<String, String> originalIdentifierMap) {
        for (BIRTypeDefinition typeDefinition : typeDefs) {
            typeDefinition.type.tsymbol.name =
                    names.fromString(
                            encodeNonFunctionIdentifier(typeDefinition.type.tsymbol.name.value, originalIdentifierMap));
            typeDefinition.internalName =
                    names.fromString(encodeNonFunctionIdentifier(typeDefinition.internalName.value,
                                                                 originalIdentifierMap));

            encodeFunctionIdentifiers(typeDefinition.attachedFuncs, names, originalIdentifierMap);
            BType bType = typeDefinition.type;
            if (bType.tag == TypeTags.OBJECT) {
                BObjectType objectType = (BObjectType) bType;
                BObjectTypeSymbol objectTypeSymbol = (BObjectTypeSymbol) bType.tsymbol;
                if (objectTypeSymbol.attachedFuncs != null) {
                    encodeAttachedFunctionIdentifiers(objectTypeSymbol.attachedFuncs, names, originalIdentifierMap);
                }
                for (BField field : objectType.fields.values()) {
                    field.name = names.fromString(encodeNonFunctionIdentifier(field.name.value, originalIdentifierMap));
                }
            }
            if (bType.tag == TypeTags.RECORD) {
                BRecordType recordType = (BRecordType) bType;
                for (BField field : recordType.fields.values()) {
                    field.name = names.fromString(encodeNonFunctionIdentifier(field.name.value, originalIdentifierMap));
                }
            }
        }
    }

    private static void encodeFunctionIdentifiers(List<BIRFunction> functions, Names names,
                                                  HashMap<String, String> originalIdentifierMap) {
        for (BIRFunction function : functions) {
            function.name = names.fromString(encodeFunctionIdentifier(function.name.value, originalIdentifierMap));
            for (BIRNode.BIRVariableDcl localVar : function.localVars) {
                if (localVar.metaVarName == null) {
                    continue;
                }
                localVar.metaVarName = encodeNonFunctionIdentifier(localVar.metaVarName, originalIdentifierMap);
            }
            for (BIRNode.BIRParameter parameter : function.requiredParams) {
                if (parameter.name == null) {
                    continue;
                }
                parameter.name = names.fromString(encodeNonFunctionIdentifier(parameter.name.value,
                                                                              originalIdentifierMap));
            }
            encodeWorkerName(function, names, originalIdentifierMap);
        }
    }

    private static void encodeWorkerName(BIRFunction function, Names names,
                                         HashMap<String, String> originalIdentifierMap) {
        if (function.workerName != null) {
            function.workerName =
                    names.fromString(encodeNonFunctionIdentifier(function.workerName.value, originalIdentifierMap));
        }
    }

    private static void encodeAttachedFunctionIdentifiers(List<BAttachedFunction> functions, Names names,
                                                          HashMap<String, String> originalIdentifierMap) {
        for (BAttachedFunction function : functions) {
            function.funcName = names.fromString(encodeFunctionIdentifier(function.funcName.value,
                                                                          originalIdentifierMap));
        }
    }

    private static void encodeGlobalVariableIdentifiers(List<BIRNode.BIRGlobalVariableDcl> globalVars,
                                                        Names names,
                                                        HashMap<String, String> originalIdentifierMap) {
        for (BIRNode.BIRGlobalVariableDcl globalVar : globalVars) {
            if (globalVar == null) {
                continue;
            }
            globalVar.name = names.fromString(encodeNonFunctionIdentifier(globalVar.name.value,
                                                                          originalIdentifierMap));
        }
    }

    // Replace encoding identifiers
    static void replaceEncodedModuleIdentifiers(BIRNode.BIRPackage module, Names names,
                                                HashMap<String, String> originalIdentifierMap) {
        replaceEncodedPackageIdentifiers(module.packageID, names, originalIdentifierMap);
        replaceEncodedGlobalVariableIdentifiers(module.globalVars, names, originalIdentifierMap);
        replaceEncodedFunctionIdentifiers(module.functions, names, originalIdentifierMap);
        replaceEncodedTypeDefIdentifiers(module.typeDefs, names, originalIdentifierMap);
    }

    private static void replaceEncodedPackageIdentifiers(PackageID packageID, Names names,
                                                         HashMap<String, String> originalIdentifierMap) {
        packageID.orgName = names.fromString(originalIdentifierMap.get(packageID.orgName.value));
        packageID.name = names.fromString(originalIdentifierMap.get(packageID.name.value));
    }

    private static void replaceEncodedTypeDefIdentifiers(List<BIRTypeDefinition> typeDefs, Names names,
                                                         HashMap<String, String> originalIdentifierMap) {
        for (BIRTypeDefinition typeDefinition : typeDefs) {
            typeDefinition.type.tsymbol.name =
                    names.fromString(
                            originalIdentifierMap.get(typeDefinition.type.tsymbol.name.value));
            typeDefinition.internalName =
                    names.fromString(originalIdentifierMap.get(typeDefinition.internalName.value));

            replaceEncodedFunctionIdentifiers(typeDefinition.attachedFuncs, names, originalIdentifierMap);
            BType bType = typeDefinition.type;
            if (bType.tag == TypeTags.OBJECT) {
                BObjectType objectType = (BObjectType) bType;
                BObjectTypeSymbol objectTypeSymbol = (BObjectTypeSymbol) bType.tsymbol;
                if (objectTypeSymbol.attachedFuncs != null) {
                    replaceEncodedAttachedFunctionIdentifiers(objectTypeSymbol.attachedFuncs, names,
                                                              originalIdentifierMap);
                }
                for (BField field : objectType.fields.values()) {
                    field.name = names.fromString(originalIdentifierMap.get(field.name.value));
                }
            }
            if (bType.tag == TypeTags.RECORD) {
                BRecordType recordType = (BRecordType) bType;
                for (BField field : recordType.fields.values()) {
                    field.name = names.fromString(originalIdentifierMap.get(field.name.value));
                }
            }
        }
    }

    private static void replaceEncodedFunctionIdentifiers(List<BIRFunction> functions, Names names,
                                                          HashMap<String, String> originalIdentifierMap) {
        for (BIRFunction function : functions) {
            String originalFuncName = originalIdentifierMap.get(function.name.value);
            // This can be null if function is added using codegen.
            if (originalFuncName != null) {
                function.name = names.fromString(originalFuncName);
            }
            for (BIRNode.BIRVariableDcl localVar : function.localVars) {
                if (localVar.metaVarName == null) {
                    continue;
                }
                localVar.metaVarName = originalIdentifierMap.get(localVar.metaVarName);
            }
            for (BIRNode.BIRParameter parameter : function.requiredParams) {
                if (parameter.name == null) {
                    continue;
                }
                parameter.name = names.fromString(originalIdentifierMap.get(parameter.name.value));
            }
            replaceEncodedWorkerName(function, names, originalIdentifierMap);
        }
    }

    private static void replaceEncodedWorkerName(BIRFunction function, Names names,
                                                 HashMap<String, String> originalIdentifierMap) {
        if (function.workerName != null) {
            function.workerName = names.fromString(originalIdentifierMap.get(function.workerName.value));
        }
    }

    private static void replaceEncodedAttachedFunctionIdentifiers(List<BAttachedFunction> functions, Names names,
                                                                  HashMap<String, String> originalIdentifierMap) {
        for (BAttachedFunction function : functions) {
            function.funcName = names.fromString(originalIdentifierMap.get(function.funcName.value));
        }
    }

    private static void replaceEncodedGlobalVariableIdentifiers(List<BIRNode.BIRGlobalVariableDcl> globalVars,
                                                                Names names,
                                                                HashMap<String, String> originalIdentifierMap) {
        for (BIRNode.BIRGlobalVariableDcl globalVar : globalVars) {
            if (globalVar == null) {
                continue;
            }
            String originalGlobalVarName = originalIdentifierMap.get(globalVar.name.value);
            if (originalGlobalVarName != null) {
                globalVar.name = names.fromString(originalGlobalVarName);
            }
        }
    }

    private static String encodeFunctionIdentifier(String functionName, HashMap<String, String> originalIdentifierMap) {
        String encodedString = IdentifierUtils.encodeFunctionIdentifier(functionName);
        originalIdentifierMap.putIfAbsent(encodedString, functionName);
        return encodedString;
    }

    private static String encodeNonFunctionIdentifier(String pkgName, HashMap<String, String> originalIdentifierMap) {
        String encodedString = IdentifierUtils.encodeNonFunctionIdentifier(pkgName);
        originalIdentifierMap.putIfAbsent(encodedString, pkgName);
        return encodedString;
    }
}
