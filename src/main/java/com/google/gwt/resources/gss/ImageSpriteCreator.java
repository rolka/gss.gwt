/*
 * Copyright 2013 Julien Dramaix.
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

import static com.google.common.css.compiler.passes.PassUtil.ALTERNATE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.css.SourceCodeLocation;
import com.google.common.css.compiler.ast.CssCommentNode;
import com.google.common.css.compiler.ast.CssCompilerPass;
import com.google.common.css.compiler.ast.CssDeclarationNode;
import com.google.common.css.compiler.ast.CssFunctionArgumentsNode;
import com.google.common.css.compiler.ast.CssFunctionNode;
import com.google.common.css.compiler.ast.CssFunctionNode.Function;
import com.google.common.css.compiler.ast.CssLiteralNode;
import com.google.common.css.compiler.ast.CssPropertyNode;
import com.google.common.css.compiler.ast.CssPropertyValueNode;
import com.google.common.css.compiler.ast.CssValueNode;
import com.google.common.css.compiler.ast.DefaultTreeVisitor;
import com.google.common.css.compiler.ast.ErrorManager;
import com.google.common.css.compiler.ast.GssError;
import com.google.common.css.compiler.ast.MutatingVisitController;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.NotFoundException;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.resources.ext.ResourceContext;
import com.google.gwt.resources.ext.ResourceGeneratorUtil;
import com.google.gwt.resources.gss.ast.CssDotPathNode;

import java.util.List;

public class ImageSpriteCreator extends DefaultTreeVisitor implements CssCompilerPass {
  private static final String SPRITE_PROPERTY_NAME = "gwt-sprite";

  private final MutatingVisitController visitController;
  private final ErrorManager errorManager;
  private final ResourceContext context;
  private final JClassType imageResourceType;
  private final String resourceThisPrefix;

  public ImageSpriteCreator(MutatingVisitController visitController, ResourceContext context,
      ErrorManager errorManager) {
    this.visitController = visitController;
    this.errorManager = errorManager;
    this.context = context;
    this.imageResourceType = context.getGeneratorContext().getTypeOracle().findType(
        ImageResource.class.getName());
    this.resourceThisPrefix = context.getImplementationSimpleSourceName() + ".this";
  }

  @Override
  public boolean enterDeclaration(CssDeclarationNode declaration) {
    String propertyName = declaration.getPropertyName().getPropertyName();

    if (SPRITE_PROPERTY_NAME.equals(propertyName)) {
      createSprite(declaration);
      return true;
    }

    return super.enterDeclaration(declaration);
  }

  @Override
  public void runPass() {
    assert imageResourceType != null;

    visitController.startVisit(this);
  }

  private CssDeclarationNode buildBackgroundDeclaration(String imageResource, String repeatText,
      SourceCodeLocation location) {
    // build the url function
    CssFunctionNode urlFunction = new CssFunctionNode(Function.byName("url"), location);
    CssDotPathNode imageUrl = new CssDotPathNode(resourceThisPrefix, imageResource + ".getSafeUri" +
        ".asString", null, null, location);
    CssFunctionArgumentsNode urlFunctionArguments = new CssFunctionArgumentsNode();
    urlFunctionArguments.addChildToBack(imageUrl);
    urlFunction.setArguments(urlFunctionArguments);

    // build left offset
    CssDotPathNode left = new CssDotPathNode(resourceThisPrefix, imageResource + ".getLeft", "-",
        "px", location);

    // build top offset
    CssDotPathNode top = new CssDotPathNode(resourceThisPrefix, imageResource + ".getTop",
        "-", "px", location);

    // build repeat
    CssLiteralNode repeat = new CssLiteralNode(repeatText, location);

    CssPropertyNode propertyNode = new CssPropertyNode("background", location);
    CssPropertyValueNode propertyValueNode = new CssPropertyValueNode(ImmutableList.of(urlFunction,
        left, top, repeat));
    propertyValueNode.setSourceCodeLocation(location);

    return createDeclarationNode(propertyNode, propertyValueNode, location, true);
  }

  private CssDeclarationNode buildHeightDeclaration(String imageResource,
      SourceCodeLocation location) {
    CssPropertyNode propertyNode = new CssPropertyNode("height", location);
    CssValueNode valueNode = new CssDotPathNode(resourceThisPrefix, imageResource + ".getHeight",
        null, "px", location);

    CssPropertyValueNode propertyValueNode = new CssPropertyValueNode(ImmutableList.of(valueNode));

    return createDeclarationNode(propertyNode, propertyValueNode, location, true);
  }

  private CssDeclarationNode buildOverflowDeclaration(SourceCodeLocation location) {
    CssPropertyNode propertyNode = new CssPropertyNode("overflow", location);
    CssValueNode valueNode = new CssLiteralNode("hidden", location);

    CssPropertyValueNode propertyValueNode = new CssPropertyValueNode(ImmutableList.of(valueNode));

    return createDeclarationNode(propertyNode, propertyValueNode, location, true);
  }

  private CssDeclarationNode buildWidthDeclaration(String imageResource,
      SourceCodeLocation location) {
    CssPropertyNode propertyNode = new CssPropertyNode("width", location);
    CssValueNode valueNode = new CssDotPathNode(resourceThisPrefix, imageResource + ".getWidth",
        null, "px", location);
    CssPropertyValueNode propertyValueNode = new CssPropertyValueNode(ImmutableList.of(valueNode));

    return createDeclarationNode(propertyNode, propertyValueNode, location, true);
  }

  private void createSprite(CssDeclarationNode declaration) {
    List<CssValueNode> valuesNodes = declaration.getPropertyValue().getChildren();

    if (valuesNodes.size() != 1) {
      errorManager.report(new GssError(SPRITE_PROPERTY_NAME + " must have exactly one value",
          declaration.getSourceCodeLocation()));
    }

    String imageResource = valuesNodes.get(0).getValue();

    JMethod imageMethod;
    try {
      imageMethod = ResourceGeneratorUtil.getMethodByPath(context.getClientBundleType(),
          getPathElement(imageResource), imageResourceType);
    } catch (NotFoundException e) {
      errorManager.report(new GssError("Unable to find ImageResource method "
          + imageResource + " in " + context.getClientBundleType().getQualifiedSourceName() + " : "
          + e.getMessage(), declaration.getSourceCodeLocation()));
      return;
    }

    ImageOptions options = imageMethod.getAnnotation(ImageOptions.class);
    RepeatStyle repeatStyle = options != null ? options.repeatStyle() : RepeatStyle.None;

    Builder<CssDeclarationNode> listBuilder = ImmutableList.builder();
    SourceCodeLocation sourceCodeLocation = declaration.getSourceCodeLocation();

    String repeatText;
    switch (repeatStyle) {
      case None:
        repeatText = " no-repeat";
        listBuilder.add(buildHeightDeclaration(imageResource, sourceCodeLocation));
        listBuilder.add(buildWidthDeclaration(imageResource, sourceCodeLocation));
        break;
      case Horizontal:
        repeatText = " repeat-x";
        listBuilder.add(buildHeightDeclaration(imageResource, sourceCodeLocation));
        break;
      case Vertical:
        repeatText = " repeat-y";
        listBuilder.add(buildWidthDeclaration(imageResource, sourceCodeLocation));
        break;
      case Both:
        repeatText = " repeat";
        break;
      default:
        errorManager.report(new GssError("Unknown repeatStyle " + repeatStyle,
            sourceCodeLocation));
        return;
    }

    listBuilder.add(buildOverflowDeclaration(sourceCodeLocation));
    listBuilder.add(buildBackgroundDeclaration(imageResource, repeatText, sourceCodeLocation));

    visitController.replaceCurrentBlockChildWith(listBuilder.build(), false);
  }

  private List<String> getPathElement(String imageResourcePath) {
    return Lists.newArrayList(imageResourcePath.split("\\."));
  }

  private CssDeclarationNode createDeclarationNode(CssPropertyNode propertyNode,
      CssPropertyValueNode propertyValueNode, SourceCodeLocation location, boolean useAlternate) {
    CssDeclarationNode replaceNode =  new CssDeclarationNode(propertyNode, propertyValueNode);
    replaceNode.setSourceCodeLocation(location);

    if (useAlternate) {
      replaceNode.setComments(ImmutableList.of(new CssCommentNode(ALTERNATE, location)));
    }

    return replaceNode;
  }
}
