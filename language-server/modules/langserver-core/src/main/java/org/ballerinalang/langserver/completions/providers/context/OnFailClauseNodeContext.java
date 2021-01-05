/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.completions.providers.context;

import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.BlockStatementNode;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.OnFailClauseNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.common.utils.SymbolUtil;
import org.ballerinalang.langserver.common.utils.completion.QNameReferenceUtil;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.providers.AbstractCompletionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Completion provider for {@link OnFailClauseNode} context.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.completion.spi.BallerinaCompletionProvider")
public class OnFailClauseNodeContext extends AbstractCompletionProvider<OnFailClauseNode> {

    public OnFailClauseNodeContext() {
        super(OnFailClauseNode.class);
    }

    @Override
    public List<LSCompletionItem> getCompletions(BallerinaCompletionContext context, OnFailClauseNode node) {
        if (this.onSuggestTypeDescriptors(context, node)) {
            NonTerminalNode symbolAtCursor = context.getNodeAtCursor();
            Predicate<Symbol> errorPredicate = SymbolUtil::isError;
            if (symbolAtCursor.kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                QualifiedNameReferenceNode qRef = (QualifiedNameReferenceNode) symbolAtCursor;
                return this.getCompletionItemList(QNameReferenceUtil.getModuleContent(context, qRef, errorPredicate),
                        context);
            }

            List<Symbol> visibleSymbols = context.visibleSymbols(context.getCursorPosition());
            List<LSCompletionItem> completionItems = this.getModuleCompletionItems(context);
            List<Symbol> errEntries = visibleSymbols.stream()
                    .filter(errorPredicate)
                    .collect(Collectors.toList());
            completionItems.addAll(this.getCompletionItemList(errEntries, context));
            completionItems.add(CommonUtil.getErrorTypeCompletionItem(context));

            return completionItems;
        }

        return new ArrayList<>();
    }

    @Override
    public boolean onPreValidation(BallerinaCompletionContext context, OnFailClauseNode node) {
        return !node.onKeyword().isMissing();
    }

    private boolean onSuggestTypeDescriptors(BallerinaCompletionContext context, OnFailClauseNode node) {
        int cursor = context.getCursorPositionInTree();
        BlockStatementNode blockStatement = node.blockStatement();
        Token failKeyword = node.failKeyword();

        if (failKeyword.isMissing()) {
            return false;
        }

        return cursor > failKeyword.textRange().endOffset()
                && (blockStatement.isMissing()
                || cursor < blockStatement.openBraceToken().textRange().endOffset());
    }
}
