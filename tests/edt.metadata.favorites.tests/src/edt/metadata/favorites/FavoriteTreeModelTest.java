/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com._1c.g5.v8.dt.metadata.mdclass.Catalog;
import com._1c.g5.v8.dt.metadata.mdclass.CatalogAttribute;
import com._1c.g5.v8.dt.metadata.mdclass.CommonModule;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdClassFactory;


public class FavoriteTreeModelTest
{
    private final UUID catalogUuid = UUID.randomUUID();

    private final UUID secondCatalogUuid = UUID.randomUUID();

    private final UUID attributeUuid = UUID.randomUUID();

    private final UUID moduleUuid = UUID.randomUUID();

    @Test
    public void groupsTopLevelObjectsUnderRussianKindLabels()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        FavoriteTreeNode catalogs = findRoot(result, "Справочники");
        assertNotNull("Ожидалась группа \"Справочники\"", catalogs);
        assertTrue(catalogs.isGroup());
        assertEquals(2, catalogs.children.size());
    }

    @Test
    public void sortsLeafObjectsAlphabetically()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        FavoriteTreeNode catalogs = findRoot(result, "Справочники");
        assertEquals(List.of("АвансовыеОтчёты", "Товары"),
            catalogs.children.stream().map(node -> node.label).toList());
    }

    @Test
    public void putsCommonKindsUnderCommonBranch()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        FavoriteTreeNode common = findRoot(result, FavoriteTreeModel.COMMON_GROUP);
        assertNotNull("Ожидалась ветка \"Общие\"", common);
        FavoriteTreeNode modules = findChild(common, "Общие модули");
        assertNotNull("Ожидалась группа \"Общие модули\" внутри \"Общие\"", modules);
        assertEquals(List.of("ОбщегоНазначения"),
            modules.children.stream().map(node -> node.label).toList());
    }

    @Test
    public void appendsNestedObjectsUnderRussianCollectionLabels()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        FavoriteTreeNode catalogNode = findChild(findRoot(result, "Справочники"), "Товары");
        FavoriteTreeNode attributes = findChild(catalogNode, "Реквизиты");
        assertNotNull("Ожидалась группа \"Реквизиты\" внутри справочника", attributes);
        FavoriteTreeNode attribute = findChild(attributes, "Артикул");
        assertNotNull(attribute);
        assertTrue(attribute.isObject());
        assertEquals(attributeUuid.toString(), attribute.target.uuid());
        assertEquals("UUID-путь листа должен начинаться с его собственного UUID",
            attributeUuid.toString(), attribute.uuidPath.get(0));
    }

    @Test
    public void collectsUuidsOfAllObjectsIncludingNested()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        assertTrue(result.existingUuids().contains(catalogUuid.toString()));
        assertTrue(result.existingUuids().contains(secondCatalogUuid.toString()));
        assertTrue(result.existingUuids().contains(attributeUuid.toString()));
        assertTrue(result.existingUuids().contains(moduleUuid.toString()));
    }

    @Test
    public void normalizesLabelsForSearchOnce()
    {
        FavoriteTreeModel.BuildResult result = FavoriteTreeModel.build(configuration());

        FavoriteTreeNode catalogNode = findChild(findRoot(result, "Справочники"), "Товары");
        assertEquals("товары", catalogNode.normalizedLabel);
    }

    private Configuration configuration()
    {
        MdClassFactory factory = MdClassFactory.eINSTANCE;
        Configuration configuration = factory.createConfiguration();

        Catalog catalog = factory.createCatalog();
        catalog.setUuid(catalogUuid);
        catalog.setName("Товары");
        CatalogAttribute attribute = factory.createCatalogAttribute();
        attribute.setUuid(attributeUuid);
        attribute.setName("Артикул");
        catalog.getAttributes().add(attribute);

        Catalog secondCatalog = factory.createCatalog();
        secondCatalog.setUuid(secondCatalogUuid);
        secondCatalog.setName("АвансовыеОтчёты");

        CommonModule module = factory.createCommonModule();
        module.setUuid(moduleUuid);
        module.setName("ОбщегоНазначения");

        configuration.getCatalogs().add(catalog);
        configuration.getCatalogs().add(secondCatalog);
        configuration.getCommonModules().add(module);
        return configuration;
    }

    private static FavoriteTreeNode findRoot(FavoriteTreeModel.BuildResult result, String label)
    {
        return result.roots().stream().filter(node -> label.equals(node.label)).findFirst().orElse(null);
    }

    private static FavoriteTreeNode findChild(FavoriteTreeNode parent, String label)
    {
        assertNotNull(parent);
        return parent.children.stream().filter(node -> label.equals(node.label)).findFirst().orElse(null);
    }
}
