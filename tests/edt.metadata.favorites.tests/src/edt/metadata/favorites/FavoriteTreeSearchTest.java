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
        assertEquals(Set.of(match), result.matchingObjectNodes());
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
        assertEquals(Set.of(form), result.matchingObjectNodes());
        assertEquals(1, result.matchingObjects());
    }

    @Test
    public void fqnMatchInParentDoesNotTurnNestedObjectsIntoMatches()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Контрагенты", "catalog", "Catalog.Контрагенты");
        FavoriteTreeNode forms = FavoriteTreeNode.group("Формы");
        FavoriteTreeNode form =
            object("Основная", "form", "Catalog.Контрагенты.Form.Основная");
        root.addChild(catalog);
        catalog.addChild(forms);
        forms.addChild(form);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root),
            "catalog.контрагенты", false, Set.of(), () -> false);

        assertTrue(result.visibleNodes().contains(catalog));
        assertFalse(result.visibleNodes().contains(forms));
        assertFalse(result.visibleNodes().contains(form));
        assertEquals(Set.of(catalog), result.matchingObjectNodes());
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

    @Test
    public void russianTypeNameFindsObjectsByAlternativeFqnPath()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Контрагенты", "catalog", "Catalog.Контрагенты");
        root.addChild(catalog);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root),
            "справочник.контр", false, Set.of(), () -> false);

        assertEquals(Set.of(catalog), result.matchingObjectNodes());
    }

    @Test
    public void objectNamedLikeMetadataTypeIsStillFoundByItsOwnPath()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Справочник", "catalog", "Catalog.Справочник");
        FavoriteTreeNode form =
            object("Основная", "form", "Catalog.Справочник.Form.Основная");
        FavoriteTreeNode otherForm =
            object("Основная", "other", "Catalog.Контрагенты.Form.Основная");
        root.addChild(catalog);
        catalog.addChild(form);
        root.addChild(otherForm);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(root),
            "справочник.form", false, Set.of(), () -> false);

        assertEquals(Set.of(form), result.matchingObjectNodes());
    }

    @Test
    public void objectNameEndingWithTypeNameIsNotMatchedByTypePath()
    {
        FavoriteTreeNode common = FavoriteTreeNode.group("Общие");
        FavoriteTreeNode services = FavoriteTreeNode.group("HTTP-сервисы");
        FavoriteTreeNode service =
            object("ExtensionCatalog", "service", "HTTPService.ExtensionCatalog");
        FavoriteTreeNode templates = FavoriteTreeNode.group("urlTemplates");
        FavoriteTreeNode template =
            object("execute", "template", "HTTPService.ExtensionCatalog.URLTemplate.execute");
        FavoriteTreeNode catalogs = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Валюты", "catalog", "Catalog.Валюты");
        common.addChild(services);
        services.addChild(service);
        service.addChild(templates);
        templates.addChild(template);
        catalogs.addChild(catalog);

        FavoriteTreeSearch.Result result = FavoriteTreeSearch.compute(List.of(common, catalogs),
            "справочник.", false, Set.of(), () -> false);

        assertEquals(Set.of(catalog), result.matchingObjectNodes());
        assertFalse(result.visibleNodes().contains(service));
        assertFalse(result.visibleNodes().contains(template));
    }

    @Test
    public void russianNestedTypeNameFindsNestedObject()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Валюты", "catalog", "Catalog.Валюты");
        FavoriteTreeNode forms = FavoriteTreeNode.group("Формы");
        FavoriteTreeNode form = object("ПараметрыПрописиВалюты_en", "form",
            "Catalog.Валюты.Form.ПараметрыПрописиВалюты_en");
        root.addChild(catalog);
        catalog.addChild(forms);
        forms.addChild(form);

        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "форма.параметры", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "form.параметры", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "валюты.форма", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "алюты.form", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "справочник.валюты.форма", false, Set.of(), () -> false)
            .matchingObjectNodes());
    }

    @Test
    public void objectNameEqualToTypeNameIsNotTreatedAsType()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Form", "catalog", "Catalog.Form");
        FavoriteTreeNode attribute = object("Code", "attribute", "Catalog.Form.CatalogAttribute.Code");
        FavoriteTreeNode form = object("Основная", "form", "Catalog.Form.CatalogForm.Основная");
        root.addChild(catalog);
        catalog.addChild(attribute);
        catalog.addChild(form);

        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "форма.", false, Set.of(), () -> false).matchingObjectNodes());
        assertEquals(Set.of(attribute), FavoriteTreeSearch
            .compute(List.of(root), "справочник.form.реквизит", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "справочник.form.форма", false, Set.of(), () -> false)
            .matchingObjectNodes());
    }

    @Test
    public void russianNestedTypeMatchesOwnerSpecificClassName()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Валюты", "catalog", "Catalog.Валюты");
        FavoriteTreeNode form =
            object("ПараметрыПрописи", "form", "Catalog.Валюты.CatalogForm.ПараметрыПрописи");
        FavoriteTreeNode attribute =
            object("Код", "attribute", "Catalog.Валюты.CatalogAttribute.Код");
        root.addChild(catalog);
        catalog.addChild(form);
        catalog.addChild(attribute);

        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "валюты.форма", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(attribute), FavoriteTreeSearch
            .compute(List.of(root), "справочник.валюты.реквизит", false, Set.of(), () -> false)
            .matchingObjectNodes());
    }

    @Test
    public void nestedTypeNameIsNotMatchedByClassNameSuffixOfAnotherType()
    {
        FavoriteTreeNode common = FavoriteTreeNode.group("Общие");
        FavoriteTreeNode commonTemplates = FavoriteTreeNode.group("Общие макеты");
        FavoriteTreeNode commonTemplate = object("demo", "commonTemplate", "CommonTemplate.demo");
        FavoriteTreeNode services = FavoriteTreeNode.group("HTTP-сервисы");
        FavoriteTreeNode service = object("Биллинг", "service", "HTTPService.Биллинг");
        FavoriteTreeNode urlTemplates = FavoriteTreeNode.group("urlTemplates");
        FavoriteTreeNode urlTemplate =
            object("Версия", "urlTemplate", "HTTPService.Биллинг.HTTPServiceURLTemplate.Версия");
        FavoriteTreeNode catalogs = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("СтраныМира", "catalog", "Catalog.СтраныМира");
        FavoriteTreeNode template = object("Классификатор", "template",
            "Catalog.СтраныМира.CatalogTemplate.Классификатор");
        common.addChild(commonTemplates);
        commonTemplates.addChild(commonTemplate);
        common.addChild(services);
        services.addChild(service);
        service.addChild(urlTemplates);
        urlTemplates.addChild(urlTemplate);
        catalogs.addChild(catalog);
        catalog.addChild(template);

        assertEquals(Set.of(commonTemplate, template), FavoriteTreeSearch
            .compute(List.of(common, catalogs), "макет.", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(urlTemplate), FavoriteTreeSearch
            .compute(List.of(common, catalogs), "шаблонurl.", false, Set.of(), () -> false)
            .matchingObjectNodes());
    }

    @Test
    public void patternSegmentMustCoverWholeFqnSegmentInTheMiddle()
    {
        FavoriteTreeNode root = FavoriteTreeNode.group("Справочники");
        FavoriteTreeNode catalog = object("Валюты", "catalog", "Catalog.Валюты");
        FavoriteTreeNode form = object("Основная", "form", "Catalog.Валюты.Form.Основная");
        root.addChild(catalog);
        catalog.addChild(form);

        assertEquals(Set.of(), FavoriteTreeSearch
            .compute(List.of(root), "валют.form", false, Set.of(), () -> false)
            .matchingObjectNodes());
        assertEquals(Set.of(form), FavoriteTreeSearch
            .compute(List.of(root), "валюты.form", false, Set.of(), () -> false)
            .matchingObjectNodes());
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
