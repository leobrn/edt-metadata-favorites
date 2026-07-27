/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;


public class FavoriteTreeSearchTest
{
    @Test
    public void keepsPathsToMatches()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode match = object("Номенклатура", "1");
        FavoriteTreeNode other = object("Контрагенты", "2");
        root.addChild(match);
        root.addChild(other);

        FavoriteTreeSearch.Result result =
            FavoriteTreeSearch.compute(List.of(root), "номен", false, Set.of(), () -> false);

        assertNotNull(result);
        assertTrue(result.visibleNodes().contains(root));
        assertTrue(result.visibleNodes().contains(match));
        assertFalse(result.visibleNodes().contains(other));
        assertEquals(1, result.matchingObjects());
    }

    @Test
    public void combinesSearchWithOnlySelected()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Документы");
        FavoriteTreeNode selected = object("Заказ", "selected");
        FavoriteTreeNode notSelected = object("Заказ поставщику", "not-selected");
        root.addChild(selected);
        root.addChild(notSelected);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root), "заказ", true,
            Set.of("selected"), () -> false);

        assertTrue(result.visibleNodes().contains(selected));
        assertFalse(result.visibleNodes().contains(notSelected));
        assertEquals(1, result.matchingObjects());
    }

    @Test
    public void parentNameDoesNotTurnNestedObjectsIntoMatches()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Контрагенты", "catalog", "Catalog.Контрагенты");
        FavoriteTreeNode forms = FavoriteTreeNode.group("Формы");
        FavoriteTreeNode form =
            object("Основная", "form", "Catalog.Контрагенты.Form.Основная");
        root.addChild(catalog);
        catalog.addChild(forms);
        forms.addChild(form);

        FavoriteTreeSearch.Result result =
            FavoriteTreeSearch.compute(List.of(root), "контрагенты", false, Set.of(), () -> false);

        assertTrue(result.visibleNodes().contains(catalog));
        assertFalse(result.visibleNodes().contains(forms));
        assertFalse(result.visibleNodes().contains(form));
        assertEquals(1, result.matchingObjects());
    }

    @Test
    public void explicitFqnSearchCanFindNestedObject()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Контрагенты", "catalog", "Catalog.Контрагенты");
        FavoriteTreeNode form =
            object("Основная", "form", "Catalog.Контрагенты.Form.Основная");
        root.addChild(catalog);
        catalog.addChild(form);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root),
            "catalog.контрагенты.form", false, Set.of(), () -> false);

        assertTrue(result.visibleNodes().contains(catalog));
        assertTrue(result.visibleNodes().contains(form));
        assertEquals(1, result.matchingObjects());
    }

    @Test
    public void countsAllMatchesWithoutTruncatingVisibleResults()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Объекты");
        for (int index = 0; index < 500; index++)
        {
            root.addChild(object("Объект " + index, Integer.toString(index)));
        }

        FavoriteTreeSearch.Result result =
            FavoriteTreeSearch.compute(List.of(root), "объект", false, Set.of(), () -> false);

        assertEquals(500, result.matchingObjects());
        assertEquals(501, result.visibleNodes().size());
    }

    @Test
    public void observesCancellationPeriodically()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Объекты");
        for (int index = 0; index < 5000; index++)
        {
            root.addChild(object("Объект " + index, Integer.toString(index)));
        }
        int[] checks = {0};

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root), "объект", false,
            Set.of(), () -> ++checks[0] >= 2);

        assertNull(result);
        assertTrue(checks[0] < 10);
    }

    @Test
    public void assignsParentWhenBuildingTree()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Корень");
        FavoriteTreeNode child = object("Объект", "uuid");

        root.addChild(child);

        assertEquals(root, child.parent);
    }

    private static FavoriteTreeNode object(String label, String uuid)
    {
        return FavoriteTreeNode.object(label, new PinTarget(uuid, label), null);
    }

    private static FavoriteTreeNode object(String label, String uuid, String fqn)
    {
        return FavoriteTreeNode.object(label, new PinTarget(uuid, fqn), null);
    }
}
