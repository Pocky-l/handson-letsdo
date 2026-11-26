package com.pockyl.handson_letsdo.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_letsdo.gametest.BowlTests;
import com.pockyl.handson_letsdo.gametest.KettleTests;
import com.pockyl.handson_letsdo.gametest.MincerTests;
import com.pockyl.handson_letsdo.gametest.PotTests;
import com.pockyl.handson_letsdo.gametest.PressTests;
import com.pockyl.handson_letsdo.gametest.RoasterTests;
import com.pockyl.handson_letsdo.gametest.StationTests;
import com.pockyl.handson_letsdo.gametest.StoveTests;

/** {@link StationTests}, {@link PotTests}, {@link StoveTests}, {@link RoasterTests}, {@link KettleTests}, {@link PressTests}, {@link MincerTests} and {@link BowlTests} on Fabric: they run when the game starts with {@code -Dfabric-api.gametest}. */
public final class FabricStationTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modLoads(GameTestHelper helper) {
        StationTests.modLoads(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBarrelIsAStationOnlyWithVinery(GameTestHelper helper) {
        StationTests.theBarrelIsAStationOnlyWithVinery(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStationsSlotsAreTheBarrelsOwn(GameTestHelper helper) {
        StationTests.theStationsSlotsAreTheBarrelsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = StationTests.WINE_TIMEOUT)
    public void wineThroughTheStationIsWineByHoppers(GameTestHelper helper) {
        StationTests.wineThroughTheStationIsWineByHoppers(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aClientsCopyOfAnEmptiedBarrelIsEmpty(GameTestHelper helper) {
        StationTests.aClientsCopyOfAnEmptiedBarrelIsEmpty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void everyWineIsLaidFromTheBookAndFerments(GameTestHelper helper) {
        StationTests.everyWineIsLaidFromTheBookAndFerments(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theHatchIsTakenOffByTheCook(GameTestHelper helper) {
        StationTests.theHatchIsTakenOffByTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBarrelFermentsOnlyShut(GameTestHelper helper) {
        StationTests.theBarrelFermentsOnlyShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everythingInTheBarrelFermentsAtOnce(GameTestHelper helper) {
        StationTests.everythingInTheBarrelFermentsAtOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theJuiceComesBackIntoBottles(GameTestHelper helper) {
        StationTests.theJuiceComesBackIntoBottles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWineIsDrawnAtTheBung(GameTestHelper helper) {
        StationTests.theWineIsDrawnAtTheBung(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStationsDataStaysSmall(GameTestHelper helper) {
        StationTests.theStationsDataStaysSmall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBarrelsSceneIsRead(GameTestHelper helper) {
        StationTests.theBarrelsSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePotIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        PotTests.thePotIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePotsSlotsAreItsOwn(GameTestHelper helper) {
        PotTests.thePotsSlotsAreItsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aStirredPotCooksAndAnUnstirredOneWaits(GameTestHelper helper) {
        PotTests.aStirredPotCooksAndAnUnstirredOneWaits(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSoupIsFilledIntoBowls(GameTestHelper helper) {
        PotTests.theSoupIsFilledIntoBowls(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unevenStacksMakeNoServingsFromNothing(GameTestHelper helper) {
        PotTests.unevenStacksMakeNoServingsFromNothing(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatAnIngredientLeavesGoesToTheCook(GameTestHelper helper) {
        PotTests.whatAnIngredientLeavesGoesToTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void starsGiveBonusServings(GameTestHelper helper) {
        PotTests.starsGiveBonusServings(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = PotTests.OWN_TIMEOUT)
    public void aHopperFedPotCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksUpstreamsWay(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aCookInTheMenuCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aCookInTheMenuCooksUpstreamsWay(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
    public void aPotLeftMidStirGoesBackToUpstreamsTick(GameTestHelper helper) {
        PotTests.aPotLeftMidStirGoesBackToUpstreamsTick(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDishThatNeedsNoBowlComesOutWhole(GameTestHelper helper) {
        PotTests.aDishThatNeedsNoBowlComesOutWhole(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void everyDishIsLaidFromTheBookAndCooks(GameTestHelper helper) {
        PotTests.everyDishIsLaidFromTheBookAndCooks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aLearnedDishCooksForTheCookWhoLearnedIt(GameTestHelper helper) {
        PotTests.aLearnedDishCooksForTheCookWhoLearnedIt(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePotTellsItsClientsWhatItHolds(GameTestHelper helper) {
        PotTests.thePotTellsItsClientsWhatItHolds(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePotsDataStaysSmall(GameTestHelper helper) {
        PotTests.thePotsDataStaysSmall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePotsSceneIsRead(GameTestHelper helper) {
        PotTests.thePotsSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightsPotIsAStationOnlyWithCandlelight(GameTestHelper helper) {
        PotTests.thePotIsAStationOnlyWithFarmAndCharm(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePotsSlotsAreItsOwn(GameTestHelper helper) {
        PotTests.thePotsSlotsAreItsOwn(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void candlelightAStirredPotCooksAndAnUnstirredOneWaits(GameTestHelper helper) {
        PotTests.aStirredPotCooksAndAnUnstirredOneWaits(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTheSoupIsFilledIntoBowls(GameTestHelper helper) {
        PotTests.theSoupIsFilledIntoBowls(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightUnevenStacksMakeNoServingsFromNothing(GameTestHelper helper) {
        PotTests.unevenStacksMakeNoServingsFromNothing(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightWhatAnIngredientLeavesGoesToTheCook(GameTestHelper helper) {
        PotTests.whatAnIngredientLeavesGoesToTheCook(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void candlelightStarsGiveBonusServings(GameTestHelper helper) {
        PotTests.starsGiveBonusServings(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = PotTests.OWN_TIMEOUT)
    public void candlelightAHopperFedPotCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksUpstreamsWay(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void candlelightAHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void candlelightACookInTheMenuCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aCookInTheMenuCooksUpstreamsWay(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
    public void candlelightAPotLeftMidStirGoesBackToUpstreamsTick(GameTestHelper helper) {
        PotTests.aPotLeftMidStirGoesBackToUpstreamsTick(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightADishThatNeedsNoBowlComesOutWhole(GameTestHelper helper) {
        PotTests.aDishThatNeedsNoBowlComesOutWhole(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTwoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void candlelightEveryDishIsLaidFromTheBookAndCooks(GameTestHelper helper) {
        PotTests.everyDishIsLaidFromTheBookAndCooks(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightALearnedDishCooksForTheCookWhoLearnedIt(GameTestHelper helper) {
        PotTests.aLearnedDishCooksForTheCookWhoLearnedIt(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePotTellsItsClientsWhatItHolds(GameTestHelper helper) {
        PotTests.thePotTellsItsClientsWhatItHolds(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePotsDataStaysSmall(GameTestHelper helper) {
        PotTests.thePotsDataStaysSmall(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePotsSceneIsRead(GameTestHelper helper) {
        PotTests.thePotsSceneIsRead(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStoveIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        StoveTests.theStoveIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStovesSlotsAreItsOwn(GameTestHelper helper) {
        StoveTests.theStovesSlotsAreItsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
    public void breadThroughTheStationIsBreadByHoppers(GameTestHelper helper) {
        StoveTests.breadThroughTheStationIsBreadByHoppers(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void takingTheDishKeepsWhatBakes(GameTestHelper helper) {
        StoveTests.takingTheDishKeepsWhatBakes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void theStoveBakesOnlyShut(GameTestHelper helper) {
        StoveTests.theStoveBakesOnlyShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void fuelGoesOnTheFireWithTheDoorShut(GameTestHelper helper) {
        StoveTests.fuelGoesOnTheFireWithTheDoorShut(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = StoveTests.THREE_BAKES)
    public void twoAlikeBakeToTheLastSet(GameTestHelper helper) {
        StoveTests.twoAlikeBakeToTheLastSet(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void everyBakingIsLaidFromTheBookAndBakes(GameTestHelper helper) {
        StoveTests.everyBakingIsLaidFromTheBookAndBakes(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aClientsCopyOfAnEmptiedStoveIsEmpty(GameTestHelper helper) {
        StoveTests.aClientsCopyOfAnEmptiedStoveIsEmpty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStovesDataStaysSmall(GameTestHelper helper) {
        StoveTests.theStovesDataStaysSmall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStovesSceneIsRead(GameTestHelper helper) {
        StoveTests.theStovesSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightsStoveIsAStationOnlyWithCandlelight(GameTestHelper helper) {
        StoveTests.theStoveIsAStationOnlyWithFarmAndCharm(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTheStovesSlotsAreItsOwn(GameTestHelper helper) {
        StoveTests.theStovesSlotsAreItsOwn(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
    public void candlelightBreadThroughTheStationIsBreadByHoppers(GameTestHelper helper) {
        StoveTests.breadThroughTheStationIsBreadByHoppers(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTakingTheDishKeepsWhatBakes(GameTestHelper helper) {
        StoveTests.takingTheDishKeepsWhatBakes(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void candlelightTheStoveBakesOnlyShut(GameTestHelper helper) {
        StoveTests.theStoveBakesOnlyShut(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void candlelightFuelGoesOnTheFireWithTheDoorShut(GameTestHelper helper) {
        StoveTests.fuelGoesOnTheFireWithTheDoorShut(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = StoveTests.THREE_BAKES)
    public void candlelightTwoAlikeBakeToTheLastSet(GameTestHelper helper) {
        StoveTests.twoAlikeBakeToTheLastSet(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void candlelightEveryBakingIsLaidFromTheBookAndBakes(GameTestHelper helper) {
        StoveTests.everyBakingIsLaidFromTheBookAndBakes(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightAClientsCopyOfAnEmptiedStoveIsEmpty(GameTestHelper helper) {
        StoveTests.aClientsCopyOfAnEmptiedStoveIsEmpty(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTheStovesDataStaysSmall(GameTestHelper helper) {
        StoveTests.theStovesDataStaysSmall(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightTheStovesSceneIsRead(GameTestHelper helper) {
        StoveTests.theStovesSceneIsRead(helper, StoveTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void everyCandlelightStoveBakesAsTheStation(GameTestHelper helper) {
        StoveTests.everyCandlelightStoveBakesAsTheStation(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRoasterIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        RoasterTests.theRoasterIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper helper) {
        RoasterTests.aSwirledRoasterRoastsAndAnUnswirledOneWaits(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = PotTests.OWN_TIMEOUT)
    public void aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper helper) {
        RoasterTests.aHopperFedRoasterRoastsUpstreamsWay(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void everyRoastIsLaidFromTheBookAndRoasts(GameTestHelper helper) {
        RoasterTests.everyRoastIsLaidFromTheBookAndRoasts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRoastersSceneIsRead(GameTestHelper helper) {
        RoasterTests.theRoastersSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRoasterTellsItsClientsWhatItHolds(GameTestHelper helper) {
        RoasterTests.theRoasterTellsItsClientsWhatItHolds(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightsPanIsAStationOnlyWithCandlelight(GameTestHelper helper) {
        RoasterTests.theRoasterIsAStationOnlyWithFarmAndCharm(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightASwirledPanRoastsAndAnUnswirledOneWaits(GameTestHelper helper) {
        RoasterTests.aSwirledRoasterRoastsAndAnUnswirledOneWaits(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = PotTests.OWN_TIMEOUT)
    public void candlelightAHopperFedPanRoastsUpstreamsWay(GameTestHelper helper) {
        RoasterTests.aHopperFedRoasterRoastsUpstreamsWay(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    public void candlelightEveryRoastIsLaidFromTheBookAndRoastsInThePan(GameTestHelper helper) {
        RoasterTests.everyRoastIsLaidFromTheBookAndRoasts(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePanTellsItsClientsWhatItHolds(GameTestHelper helper) {
        RoasterTests.theRoasterTellsItsClientsWhatItHolds(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void candlelightThePansSceneIsRead(GameTestHelper helper) {
        RoasterTests.theRoastersSceneIsRead(helper, RoasterTests.CANDLELIGHT);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theKettleIsAStationOnlyWithHerbalBrews(GameTestHelper helper) {
        KettleTests.theKettleIsAStationOnlyWithHerbalBrews(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theKettlesSlotsAreItsOwn(GameTestHelper helper) {
        KettleTests.theKettlesSlotsAreItsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void waterIsPouredByTheKettleAndTheBucketComesBack(GameTestHelper helper) {
        KettleTests.waterIsPouredByTheKettleAndTheBucketComesBack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatWasLeftInTheWaterSlotGoesToWhoeverPoursNext(GameTestHelper helper) {
        KettleTests.whatWasLeftInTheWaterSlotGoesToWhoeverPoursNext(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aTeaBrewsOnlyWithWaterAndHeatEnough(GameTestHelper helper) {
        KettleTests.aTeaBrewsOnlyWithWaterAndHeatEnough(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aKettleBrewsIntoItselfAndIsPouredAtTheSpout(GameTestHelper helper) {
        KettleTests.aKettleBrewsIntoItselfAndIsPouredAtTheSpout(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void everyTeaIsLaidFromTheBookAndBrews(GameTestHelper helper) {
        KettleTests.everyTeaIsLaidFromTheBookAndBrews(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theKettlesSceneIsRead(GameTestHelper helper) {
        KettleTests.theKettlesSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePressIsAStationOnlyWithVinery(GameTestHelper helper) {
        PressTests.thePressIsAStationOnlyWithVinery(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePressesSlotsAreItsOwn(GameTestHelper helper) {
        PressTests.thePressesSlotsAreItsOwn(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anAppleLaidAtTheStationIsPressedAndBottled(GameTestHelper helper) {
        PressTests.anAppleLaidAtTheStationIsPressedAndBottled(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePressWaitsWhileItsBasketIsOpen(GameTestHelper helper) {
        PressTests.thePressWaitsWhileItsBasketIsOpen(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mashLaidByHandIsNoPressing(GameTestHelper helper) {
        PressTests.mashLaidByHandIsNoPressing(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everythingThePressBottlesIsLaidFromTheBook(GameTestHelper helper) {
        PressTests.everythingThePressBottlesIsLaidFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void anAppleRunsIntoTheTubAndIsBottledThere(GameTestHelper helper) {
        PressTests.anAppleRunsIntoTheTubAndIsBottledThere(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aClickFillsEveryBottleTheTubHolds(GameTestHelper helper) {
        PressTests.aClickFillsEveryBottleTheTubHolds(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePressesSceneIsRead(GameTestHelper helper) {
        PressTests.thePressesSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theMincerIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        MincerTests.theMincerIsAStationOnlyWithFarmCharm(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aLoadOfUpToEightTakesTwoTurns(GameTestHelper helper) {
        MincerTests.aLoadOfUpToEightTakesTwoTurns(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void nothingIsMincedWithoutTheHand(GameTestHelper helper) {
        MincerTests.nothingIsMincedWithoutTheHand(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void whatIsMincedWaitsUnderTheSpout(GameTestHelper helper) {
        MincerTests.whatIsMincedWaitsUnderTheSpout(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatDoesNotFitGoesToTheCook(GameTestHelper helper) {
        MincerTests.whatDoesNotFitGoesToTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everythingTheMincerMakesIsLaidFromTheBook(GameTestHelper helper) {
        MincerTests.everythingTheMincerMakesIsLaidFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theMincersSceneIsRead(GameTestHelper helper) {
        MincerTests.theMincersSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aHeavyLoadTurnsSlowly(GameTestHelper helper) {
        MincerTests.aHeavyLoadTurnsSlowly(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBowlIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        BowlTests.theBowlIsAStationOnlyWithFarmCharm(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aBowlfulIsMixedByItsCircles(GameTestHelper helper) {
        BowlTests.aBowlfulIsMixedByItsCircles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void nothingIsMixedWithoutTheHand(GameTestHelper helper) {
        BowlTests.nothingIsMixedWithoutTheHand(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everythingTheBowlMakesIsLaidFromTheBook(GameTestHelper helper) {
        BowlTests.everythingTheBowlMakesIsLaidFromTheBook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBowlsSceneIsRead(GameTestHelper helper) {
        BowlTests.theBowlsSceneIsRead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatTheBowlsIngredientsLeaveGoesToTheCook(GameTestHelper helper) {
        BowlTests.whatTheBowlsIngredientsLeaveGoesToTheCook(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void aFigureOfEightMixesAsCirclesDo(GameTestHelper helper) {
        BowlTests.aFigureOfEightMixesAsCirclesDo(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWhiskGoesNoFasterThanTheMixtureLets(GameTestHelper helper) {
        BowlTests.theWhiskGoesNoFasterThanTheMixtureLets(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aThickBowlfulCountsAsFastAsAThinOne(GameTestHelper helper) {
        BowlTests.aThickBowlfulCountsAsFastAsAThinOne(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theResultIsTakenFromTheBadge(GameTestHelper helper) {
        BowlTests.theResultIsTakenFromTheBadge(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPouredLiquidIsDrawnBackByItsContainer(GameTestHelper helper) {
        BowlTests.aPouredLiquidIsDrawnBackByItsContainer(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aLoadOfUpToEightIsPressedByFourPresses(GameTestHelper helper) {
        PressTests.aLoadOfUpToEightIsPressedByFourPresses(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void nothingIsPressedWithoutTheHand(GameTestHelper helper) {
        PressTests.nothingIsPressedWithoutTheHand(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void theJuiceOfALoadRunsQuickly(GameTestHelper helper) {
        PressTests.theJuiceOfALoadRunsQuickly(helper);
    }
}
