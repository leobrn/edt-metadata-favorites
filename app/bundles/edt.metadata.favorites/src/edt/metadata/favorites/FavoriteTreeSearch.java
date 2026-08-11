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

    private static final int NO_MATCH = -1;

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
        Query query = Query.of(pattern);
        for (FavoriteTreeNode root : roots)
        {
            collect(root, query, onlySelected, selectedUuids, accumulator);
            if (accumulator.wasCanceled())
            {
                return null;
            }
        }
        return canceled.getAsBoolean() ? null
            : new Result(accumulator.visibleNodes, accumulator.matchingObjectNodes,
                accumulator.matchingObjects);
    }

    private static boolean collect(FavoriteTreeNode node, Query query, boolean onlySelected,
        Set<String> selectedUuids, Accumulator accumulator)
    {
        if (accumulator.isCanceled())
        {
            return false;
        }

        boolean objectMatches = node.isObject() && matchesPattern(node, query)
            && (!onlySelected || selectedUuids.contains(node.target.uuid()));
        boolean subtreeMatches = objectMatches;

        if (objectMatches)
        {
            accumulator.matchingObjectNodes.add(node);
            accumulator.matchingObjects++;
        }

        for (FavoriteTreeNode child : node.children)
        {
            subtreeMatches |= collect(child, query, onlySelected, selectedUuids, accumulator);
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

    private static boolean matchesPattern(FavoriteTreeNode node, Query query)
    {
        return node.normalizedLabel.contains(query.text())
            || query.hasPath() && fqnMatchesOwnPath(node, query);
    }

    private static boolean fqnMatchesOwnPath(FavoriteTreeNode node, Query query)
    {
        String fqn = node.normalizedFqn;
        if (fqn.isEmpty())
        {
            return false;
        }

        int inheritedFqnLength = inheritedFqnLength(node);
        int segmentStart = 0;
        int segmentIndex = 0;
        while (true)
        {
            if (matchFromSegment(fqn, segmentStart, segmentIndex, query) > inheritedFqnLength)
            {
                return true;
            }
            int separator = fqn.indexOf('.', segmentStart);
            if (separator < 0)
            {
                return false;
            }
            segmentStart = separator + 1;
            segmentIndex++;
        }
    }

    private static int inheritedFqnLength(FavoriteTreeNode node)
    {
        for (FavoriteTreeNode parent = node.parent; parent != null; parent = parent.parent)
        {
            if (parent.isObject())
            {
                return node.normalizedFqn.startsWith(parent.normalizedFqn)
                    ? parent.normalizedFqn.length() : 0;
            }
        }
        return 0;
    }

    private static int matchFromSegment(String fqn, int segmentStart, int segmentIndex, Query query)
    {
        int segmentEnd = segmentEnd(fqn, segmentStart);
        boolean firstMatches = isTypeSegment(segmentIndex) && query.kinds()[0] != null
            ? segmentMatchesTypeName(fqn, segmentStart, segmentEnd, query, 0)
            : segmentEndsWith(fqn, segmentStart, segmentEnd, query.segments()[0]);
        if (!firstMatches)
        {
            return NO_MATCH;
        }

        int lastIndex = query.segments().length - 1;
        int cursor = segmentEnd;
        for (int index = 1; index < lastIndex; index++)
        {
            if (cursor >= fqn.length())
            {
                return NO_MATCH;
            }
            int nextStart = cursor + 1;
            int nextEnd = segmentEnd(fqn, nextStart);
            if (!segmentEquals(fqn, nextStart, nextEnd, query.segments()[index])
                && !(isTypeSegment(segmentIndex + index)
                    && segmentMatchesTypeName(fqn, nextStart, nextEnd, query, index)))
            {
                return NO_MATCH;
            }
            cursor = nextEnd;
        }

        if (cursor >= fqn.length())
        {
            return NO_MATCH;
        }
        int lastStart = cursor + 1;
        int lastEnd = segmentEnd(fqn, lastStart);
        String last = query.segments()[lastIndex];
        if (lastEnd - lastStart >= last.length() && fqn.startsWith(last, lastStart))
        {
            return lastStart + last.length();
        }
        return isTypeSegment(segmentIndex + lastIndex)
            && segmentMatchesTypeName(fqn, lastStart, lastEnd, query, lastIndex) ? lastEnd : NO_MATCH;
    }


    private static boolean isTypeSegment(int fqnSegmentIndex)
    {
        return (fqnSegmentIndex & 1) == 0;
    }

    private static int segmentEnd(String fqn, int segmentStart)
    {
        int separator = fqn.indexOf('.', segmentStart);
        return separator < 0 ? fqn.length() : separator;
    }

    private static boolean segmentEndsWith(String fqn, int segmentStart, int segmentEnd, String value)
    {
        return segmentEnd - segmentStart >= value.length()
            && fqn.startsWith(value, segmentEnd - value.length());
    }

    private static boolean segmentEquals(String fqn, int segmentStart, int segmentEnd, String value)
    {
        return segmentEnd - segmentStart == value.length() && fqn.startsWith(value, segmentStart);
    }

    private static boolean segmentMatchesTypeName(String fqn, int segmentStart, int segmentEnd,
        Query query, int index)
    {
        String kind = query.kinds()[index];
        if (kind == null)
        {
            return false;
        }
        if (segmentEquals(fqn, segmentStart, segmentEnd, query.segments()[index])
            || segmentEquals(fqn, segmentStart, segmentEnd, kind))
        {
            return true;
        }
        return query.nestedKinds()[index] && segmentEnd - segmentStart > kind.length()
            && fqn.startsWith(kind, segmentEnd - kind.length())
            && MetadataTypeNames.isClassNameOfNestedKind(fqn.substring(segmentStart, segmentEnd), kind);
    }

    private record Query(String text, String[] segments, String[] kinds, boolean[] nestedKinds)
    {
        static Query of(String pattern)
        {
            String[] segments = pattern.split("\\.", -1);
            String[] kinds = new String[segments.length];
            boolean[] nestedKinds = new boolean[segments.length];
            for (int index = 0; index < segments.length; index++)
            {
                kinds[index] = MetadataTypeNames.kindForTypeName(segments[index]);
                nestedKinds[index] = MetadataTypeNames.isNestedTypeName(segments[index]);
            }
            return new Query(pattern, segments, kinds, nestedKinds);
        }

        boolean hasPath()
        {
            return segments.length > 1;
        }
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
