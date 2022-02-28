package MinerTools;

import MinerTools.ui.Dialogs.*;
import arc.math.*;
import arc.struct.*;
import mindustry.ai.types.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.units.UnitFactory.*;
import mindustry.world.consumers.*;

import static mindustry.Vars.*;

public class MinerUtils{
    public static float fontScale = 0.75f;
    public static float imgSize = iconSmall * fontScale;

    public static Item lastDropItem;
    private static final Seq<Class> blackDropBuild = Seq.with(StorageBlock.class, ItemBridge.class, Autotiler.class, MassDriver.class, NuclearReactor.class);
    private static final Seq<DropBuilding> buildings = new Seq<>();

    public static int countMiner(Team team){
        return team.data().units.count(unit -> unit.controller() instanceof MinerAI);
    }

    public static int countPlayer(Team team){
        return Groups.player.count(player -> player.team() == team);
    }

    public static void rebuildBlocks(){
        if(!player.unit().canBuild()) return;

        int i = 0;
        for(BlockPlan block : player.team().data().blocks){
            if(Mathf.len(block.x - player.tileX(), block.y - player.tileY()) >= buildingRange) continue;
            if(++i > 511) break;
            player.unit().addBuild(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config));
        }
    }

    /*public static void dropItems(){
        indexer.eachBlock(player.team(), player.x, player.y, itemTransferRange,
            build -> build.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0 && !(build.block instanceof CoreBlock || build.block instanceof ItemBridge ||build.block instanceof Autotiler ||build.block instanceof MassDriver),
            build -> Call.transferInventory(player, build)
        );
    }*/

    public static void dropItems(){
        buildings.clear();

        CoreBuild core = player.closestCore();
        if(core == null && !player.unit().hasItem()) return;

        boolean autoDrop = player.dst(core) <= itemTransferRange && core.items != null;

        indexer.eachBlock(player.team(), player.x, player.y, itemTransferRange,
        building -> !blackDropBuild.contains(clazz -> clazz.isAssignableFrom(building.block.getClass())), building -> {
            DropBuilding db = new DropBuilding(building, autoDrop);
            if(db.any()){
                buildings.add(db);
            }
        });

        if(buildings.isEmpty()) return;

        buildings.sort(db -> db.status.ordinal());

        for(DropBuilding db : buildings){
            if(db.drop()) break;
        }
    }

    private static void tryDropItem(Item item, int amount, boolean removed){
        for(DropBuilding db : buildings){
            Building build = db.building;
            if(build.acceptStack(item, amount, player.unit()) > 0){
                Call.transferInventory(player, build);

                if(!player.unit().hasItem()) break;
                if(removed) buildings.remove(db);
            }
        }

        lastDropItem = item;
    }

    private static void requestItem(Item item){
        CoreBuild core = player.closestCore();

        /* 玩家有物品, 需要扔回核心 */
        if(player.unit().hasItem() && player.unit().item() != item){
            // 核心是否接受
            if(core.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0){
                Call.transferInventory(player, core);
            }else{
                Call.dropItem(0);
            }
        }

        /* 拿物品 */
        if(!player.unit().hasItem()){
            int coreAmount = core.items.get(item);
            if(coreAmount > 0){
                int getAmount = Math.min(coreAmount, player.unit().maxAccepted(item));
                Call.requestItem(player, core, item, getAmount);
            }
        }
    }

    private static ItemStack[] getConsItemStack(Building building){
        if(building.block instanceof ItemTurret) return ItemStack.empty; // Not null

        if(building.block instanceof UnitFactory block){
            UnitFactoryBuild factoryBuild = (UnitFactoryBuild)building;
            int currentPlan = factoryBuild.currentPlan;
            if(currentPlan == -1) return null;
            return block.plans.get(currentPlan).requirements;
        }else if(building.block.consumes.has(ConsumeType.item)){
            Consume consume = building.block.consumes.get(ConsumeType.item);

            if(consume instanceof ConsumeItems consumeItems){
                return consumeItems.items;
            }
        }

        return null;
    }

    public static class DropBuilding{
        public DropStatus status;

        public Building building;
        public ItemStack[] consumeItems;

        public DropBuilding(Building building, boolean autoDrop){
            this.building = building;
            consumeItems = MinerUtils.getConsItemStack(building);

            if(lastDropItem != null && !player.unit().hasItem()){
                status = DropStatus.LAST;
            }else if(autoDrop && consumeItems != null){
                status = DropStatus.AUTO;
            }else if(player.unit().hasItem() && building.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0){
                status = DropStatus.PLAYER;
            }
        }

        public boolean any(){
            return status != null;
        }

        public Item getConsItem(){
            if(consumeItems == null){
                return null;
            }

            CoreBuild core = player.closestCore();

            if(building.block instanceof ItemTurret block){
                for(Item item : DropSettingDialog.settings.get(block)){
                    if(building.acceptItem(building, item) && core.items.has(item)){
                        return item;
                    }
                }
            }

            for(ItemStack stack : consumeItems){
                Item consItem = stack.item;
                int maxAmount = building.getMaximumAccepted(consItem);
                boolean buildHasItem = building.items.has(consItem), chasItem = core.items.has(consItem);

                /* 必要前提 核心有物品*/
                /* 可选的情况:
                 1.建筑没有物品
                 2.建筑有物品但是不够
                 */
                if(chasItem && (!buildHasItem || building.items.get(consItem) < maxAmount)){
                    return consItem;
                }
            }

            return null;
        }

        public boolean drop(){
            switch(status){
                case PLAYER -> {
                    tryDropItem(player.unit().item(), player.unit().stack.amount, true);
                    return true;
                }
                case LAST -> {
                    requestItem(lastDropItem);
                    tryDropItem(lastDropItem, player.unit().stack.amount, true);
                    return true;
                }
                case AUTO -> {
                    Item dropItem = getConsItem();
                    if(dropItem == null) return false;

                    requestItem(dropItem);
                    tryDropItem(dropItem, player.unit().stack.amount, true);
                    return true;
                }
            }
            return false;
        }
    }

    public enum DropStatus{
        PLAYER, LAST, AUTO
    }

}
