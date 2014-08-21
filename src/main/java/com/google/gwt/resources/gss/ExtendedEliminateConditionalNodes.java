/*
 * Copyright 2014 Julien Dramaix.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.resources.gss;

import com.google.common.collect.Lists;
import com.google.common.css.compiler.ast.CssAtRuleNode.Type;
import com.google.common.css.compiler.ast.CssBooleanExpressionNode;
import com.google.common.css.compiler.ast.CssCompilerPass;
import com.google.common.css.compiler.ast.CssConditionalBlockNode;
import com.google.common.css.compiler.ast.CssConditionalRuleNode;
import com.google.common.css.compiler.ast.DefaultTreeVisitor;
import com.google.common.css.compiler.ast.MutatingVisitController;
import com.google.common.css.compiler.passes.BooleanExpressionEvaluator;
import com.google.common.css.compiler.passes.EliminateConditionalNodes;
import com.google.gwt.resources.gss.ast.CssRuntimeConditionalRuleNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A compiler pass that eliminates the conditional blocks for which the boolean
 * expression does not evaluate to true.
 * This compiler pass doesn't treat conditional node with condition that has to be evaluated at
 * runtime.
 */
public class ExtendedEliminateConditionalNodes extends DefaultTreeVisitor implements
    CssCompilerPass {

  private final MutatingVisitController visitController;
  private final Set<String> trueConditions;
  private final Set<CssConditionalBlockNode> runtimeConditionalNodes;
  private final EliminateConditionalNodes delegate;

  private Set<CssConditionalBlockNode> alreadyTreatedNode;

  public ExtendedEliminateConditionalNodes(MutatingVisitController visitController,
      Set<String> trueConditions, Set<CssConditionalBlockNode> runtimeConditionalNodes) {
    this.visitController = visitController;
    this.trueConditions = trueConditions;
    this.runtimeConditionalNodes = runtimeConditionalNodes;
    this.delegate = new EliminateConditionalNodes(visitController, trueConditions);
  }

  @Override
  public boolean enterConditionalBlock(CssConditionalBlockNode block) {
    if (alreadyTreatedNode.contains(block)) {
      // don't visit this block again but visit its children
      return true;
    }

    if (runtimeConditionalNodes.contains(block)) {
      return enterRuntimeConditionalBlock(block);
    } else {
      // block without any runtime condition.
      return delegate.enterConditionalBlock(block);
    }
  }

  private boolean enterRuntimeConditionalBlock(CssConditionalBlockNode block) {
    boolean runtimeEvaluationNodeFound = false;
    List<CssConditionalRuleNode> newChildren =
        new ArrayList<CssConditionalRuleNode>(block.numChildren());

    for (CssConditionalRuleNode currentConditional : block.childIterable()) {
      if (currentConditional.getType() == Type.ELSE) {
        newChildren.add(currentConditional);
        break;
      }

      if (currentConditional instanceof CssRuntimeConditionalRuleNode) {
        runtimeEvaluationNodeFound = true;
        newChildren.add(currentConditional);
        continue;
      }

      // The node can be evaluated at compile time
      BooleanExpressionEvaluator evaluator = new BooleanExpressionEvaluator(
          currentConditional.getCondition(), trueConditions);

      CssBooleanExpressionNode result = evaluator.evaluate();
      boolean isTrue = CssBooleanExpressionNode.Type.TRUE_CONSTANT.equals(result.getValue());

      if (!isTrue) {
        // any node evaluated to false can be removed
      } else if (!runtimeEvaluationNodeFound) {
        // node evaluated to true before the runtime condition, replace the conditional block by the
        // children of this current conditional node.
        visitController.replaceCurrentBlockChildWith(currentConditional.getBlock().getChildren(),
            true);
        return true;
      } else {
        // node evaluated to true before the runtime condition, transform this node to an else node
        CssConditionalRuleNode newNode = new CssConditionalRuleNode(Type.ELSE,
            currentConditional.getName(), null, currentConditional.getBlock());

        newChildren.add(newNode);
        break;
      }
    }

    CssConditionalBlockNode newNode = new CssConditionalBlockNode();
    for (CssConditionalRuleNode child : newChildren) {
      newNode.addChildToBack(child);
    }

    visitController.replaceCurrentBlockChildWith(Lists.newArrayList(newNode), true);

    // mark this node as already visited.
    alreadyTreatedNode.add(newNode);

    return true;
  }

  @Override
  public void runPass() {
    alreadyTreatedNode = new HashSet<CssConditionalBlockNode>();

    visitController.startVisit(this);
  }
}
