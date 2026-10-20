// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
// CLI transformSectionList hook: emit text nodes, never raw heading HTML.
export const transformSectionList = nodes => props => ({
 type:'element',tagName:'ol',properties:{},children:nodes.map((node,i)=>({
  type:'element',tagName:'li',properties:{'data-section-level':node.level},children:[
   {type:'element',tagName:node.href?'a':'span',properties:node.href?{href:node.href}:{},
    children:[{type:'text',value:node.headingText}]},
   ...props[i].children,
  ],
 })),
});
