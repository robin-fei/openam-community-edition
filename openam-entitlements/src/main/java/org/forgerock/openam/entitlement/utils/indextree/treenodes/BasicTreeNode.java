/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013 ForgeRock Inc.
 */
package org.forgerock.openam.entitlement.utils.indextree.treenodes;

/**
 * Provides a basic abstract representation of a tree node.
 *
 * @author apforrest
 */
public abstract class BasicTreeNode implements TreeNode {

    private TreeNode parent;
    private TreeNode child;
    private TreeNode sibling;

    /**
     * End point count represents the number of added rules that end at this tree node.
     * Due to its atomic nature this variable needs to be thread safe. Several options
     * have been considered and it was a balancing act between performance and memory
     * consumption. In the end intrinsic synchronisation was the preferred choice as
     * it still gave good performance in this scenario, whereas AtomicInteger had a big
     * impact on memory as tree node count reached the 100,000s.
     */
    private volatile int endPointCount;

    @Override
    public void markEndPoint() {
        // It is essential that markEndPoint() and removeEndPoint() are
        // synchronised to ensure the integrity of the increment.
        synchronized (this) {
            endPointCount++;
        }
    }

    @Override
    public void removeEndPoint() {
        // It is essential that markEndPoint() and removeEndPoint() are
        // synchronised to ensure the integrity of the decrement.
        synchronized (this) {
            if (endPointCount > 0) {
                endPointCount--;
            }
        }
    }

    @Override
    public boolean isEndPoint() {
        // The read has not been synchronised to improve the performance of
        // read, therefore a read may not return the most up to date value.
        return endPointCount > 0;
    }

    @Override
    public boolean isLeafNode() {
        return child == null;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public boolean isWildcard() {
        return false;
    }

    @Override
    public String getFullPath() {
        StringBuilder view = new StringBuilder();
        view.append(getNodeValue());

        TreeNode p = parent;
        while (p != null && !p.isRoot()) {
            view.append(p.getNodeValue());
            p = p.getParent();
        }

        // Reversing the string is faster than append insert.
        return view.reverse().toString();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public TreeNode getChild() {
        return child;
    }

    @Override
    public void setChild(TreeNode child) {
        this.child = child;
    }

    @Override
    public boolean hasChild() {
        return child != null;
    }

    @Override
    public TreeNode getSibling() {
        return sibling;
    }

    @Override
    public void setSibling(TreeNode sibling) {
        this.sibling = sibling;
    }

    @Override
    public boolean hasSibling() {
        return sibling != null;
    }

    /**
     * @return The tree nodes depth within the tree.
     */
    protected int depth() {
        int depth = 0;

        TreeNode p = parent;
        while (p != null && !p.isRoot()) {
            depth++;
            p = p.getParent();
        }

        return depth;
    }

    @Override
    public String toString(boolean includeEndPoint) {
        StringBuilder view = new StringBuilder();

        if (!isRoot()) {
            view.append(getNodeValue());
        }

        if (includeEndPoint) {
            synchronized (this) {
                if (endPointCount > 0) {
                    // Add the rule count
                    view.append('(');
                    view.append(endPointCount);
                    view.append(')');
                }
            }
        }

        if (child != null) {
            // Add the child references
            view.append(child.toString(includeEndPoint));
        }

        if (sibling != null) {
            // Add sibling references
            view.append('\n');
            for (int i = 0, l = depth(); i < l; i++) {
                view.append(' ');
            }
            view.append(sibling.toString(includeEndPoint));
        }

        return view.toString();
    }

    /**
     * @return Tree path representation of this node.
     */
    @Override
    public String toString() {
        return getFullPath();
    }

}
