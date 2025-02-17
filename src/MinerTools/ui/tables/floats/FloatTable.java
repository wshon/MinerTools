package MinerTools.ui.tables.floats;

import MinerTools.*;
import MinerTools.ui.*;
import MinerTools.ui.settings.*;
import MinerTools.ui.tables.*;
import arc.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import org.jetbrains.annotations.ApiStatus.*;

import static arc.Core.scene;
import static mindustry.Vars.*;
import static mindustry.ui.Styles.*;

public class FloatTable extends DraggableTable implements Addable{
    private Table title;
    private Table cont;

    private boolean showCont = true;

    public FloatTable(String name){
        super(name, true);

        init();

        setDraggier(title);
        setLastPos();

        setup();

        update(this::update);

        visibility = () -> !state.isMenu() && ui.hudfrag.shown && !ui.minimapfrag.shown();
    }

    @Override
    public void addUI(){
        scene.add(this);
        invalidateHierarchy();
    }

    /**
     * 在rebuildCont方法执行前初始化变量
     */
    protected void init(){
        title = new Table(black6);
        cont = new Table();

        addSettings();
    }

    protected void addSettings(){
        addSettings(MinerVars.ui.settings.ui.addCategory(name));
    }

    protected void addSettings(MSettingTable uiSettings){
        uiSettings.checkPref("floats." + name + ".shown", true, b -> {
            if(b){
                addUI();
            }else{
                remove();
            }
        }).change();
    }

    private void setup(){
        setupTitle();
        setupCont(cont);

        add(title).fillX().minWidth(250f);

        row();

        collapser(cont, false, () -> showCont).growX().top().left();

        invalidateHierarchy();
    }

    @OverrideOnly
    protected void setupCont(Table cont){
    }

    private void setupTitle(){
        title.clearChildren();

        title.add(Core.bundle.get("miner-tools.floats." + name)).padLeft(3f).growX().left();

        title.table(buttons -> {
            buttons.defaults().width(35f).growY().right();

            setupButtons(buttons);

            buttons.button(isLocked() ? Icon.lockSmall : Icon.lockOpenSmall, clearNoneTogglei, this::toggleLocked).checked(b -> {
                b.getStyle().imageUp = (isLocked() ? Icon.lockSmall : Icon.lockOpenSmall);
                return isLocked();
            });

            buttons.button(showCont ? Icon.upSmall : Icon.downSmall, clearNonei, this::toggleCont).update(b -> b.getStyle().imageUp = (showCont ? Icon.upSmall : Icon.downSmall));

            buttons.button("x", MStyles.clearPartial2t, () -> {
                MinerVars.settings.put("floats." + name + ".shown", false);
                remove();
            }).size(35f);
        }).growY().right();
    }

    @OverrideOnly
    protected void setupButtons(Table buttons){
    }

    @OverrideOnly
    protected void update(){
        pack();
        keepInStage();
    }

    private void toggleCont(){
        showCont = !showCont;

        if(showCont){
            y -= cont.getPrefHeight();
        }else{
            y += cont.getPrefHeight();
        }
    }
}
