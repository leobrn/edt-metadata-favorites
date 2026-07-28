/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;


final class FavoriteTreeSearch
{
    private static final int CANCELLATION_CHECK_MASK = 4095;

    private FavoriteTreeSearch()
    {
    }

    record Result(Set<FavoriteTreeNode> visibleNodes,
        Set<FavoriteTreeNode> matchingObjectNodes, int matchingObjects)
    {
    }

    static Result compute(List<FavoriteTreeNode> roots, String pattern, boolean onlySelected,
        Set<String> selectedUuids, BooleanSupplier canceled)
    {
        Accumulator accumulator = new Accumulator(canceled);
        if (canceled.getAsBoolean())
        {
            return null;
        }
        for (FavoriteTreeNode root : roots)
        {
            collect(root, pattern, onlySelected, selectedUuids, accumulator);
            if (accumulator.wasCanceled())
            {
                return null;
            }
        }
        return canceled.getAsBoolean() ? null
            : new Result(accumulator.visibleNodes, accumulator.matchingObjectNodes,
                accumulator.matchingObjects);
    }

    private static boolean collect(FavoriteTreeNode node, String pattern, boolean onlySelected,
        Set<String> selectedUuids, Accumulator accumulator)
    {
        if (accumulator.isCanceled())
        {
            return false;
        }

        boolean explicitFqnSearch = pattern.indexOf('.') >= 0;
        boolean objectMatches = node.isObject()
            && (node.normalizedLabel.contains(pattern)
                || explicitFqnSearch && node.normalizedFqn.contains(pattern))
            && (!onlySelected || selectedUuids.contains(node.target.uuid()));
        boolean subtreeMatches = objectMatches;

        if (objectMatches)
        {
            accumulator.matchingObjectNodes.add(node);
            accumulator.matchingObjects++;
        }

        for (FavoriteTreeNode child : node.children)
        {
            subtreeMatches |= collect(child, pattern, onlySelected, selectedUuids, accumulator);
            if (accumulator.wasCanceled())
            {
                return false;
            }
        }

        if (subtreeMatches)
        {
            accumulator.visibleNodes.add(node);
        }
        return subtreeMatches;
    }

    private static final class Accumulator
    {
        private final Set<FavoriteTreeNode> visibleNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());

        private final Set<FavoriteTreeNode> matchingObjectNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());

        private final BooleanSupplier canceled;

        private int matchingObjects;

        private int visitedNodes;

        private boolean wasCanceled;

        Accumulator(BooleanSupplier canceled)
        {
            this.canceled = canceled;
        }

        boolean isCanceled()
        {
            visitedNodes++;
            if ((visitedNodes & CANCELLATION_CHECK_MASK) == 0
                && canceled.getAsBoolean())
            {
                wasCanceled = true;
            }
            return wasCanceled;
        }

        boolean wasCanceled()
        {
            return wasCanceled;
        }
    }
}
