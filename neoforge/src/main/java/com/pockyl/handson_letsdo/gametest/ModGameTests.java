package com.pockyl.handson_letsdo.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_letsdo.HandsOnLetsDo;

/** {@link StationTests}, {@link PotTests}, {@link StoveTests}, {@link RoasterTests}, {@link KettleTests}, {@link PressTests}, {@link MincerTests} and {@link BowlTests} on NeoForge. */
@GameTestHolder(HandsOnLetsDo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        StationTests.modLoads(helper);
    }

    @GameTest(template = "room")
    public static void theBarrelIsAStationOnlyWithVinery(GameTestHelper helper) {
        StationTests.theBarrelIsAStationOnlyWithVinery(helper);
    }

    @GameTest(template = "room")
    public static void theStationsSlotsAreTheBarrelsOwn(GameTestHelper helper) {
        StationTests.theStationsSlotsAreTheBarrelsOwn(helper);
    }

    @GameTest(template = "room", timeoutTicks = StationTests.WINE_TIMEOUT)
    public static void wineThroughTheStationIsWineByHoppers(GameTestHelper helper) {
        StationTests.wineThroughTheStationIsWineByHoppers(helper);
    }

    @GameTest(template = "room")
    public static void aClientsCopyOfAnEmptiedBarrelIsEmpty(GameTestHelper helper) {
        StationTests.aClientsCopyOfAnEmptiedBarrelIsEmpty(helper);
    }

    @GameTest(template = "room", timeoutTicks = 400)
    public static void everyWineIsLaidFromTheBookAndFerments(GameTestHelper helper) {
        StationTests.everyWineIsLaidFromTheBookAndFerments(helper);
    }

    @GameTest(template = "room")
    public static void theHatchIsTakenOffByTheCook(GameTestHelper helper) {
        StationTests.theHatchIsTakenOffByTheCook(helper);
    }

    @GameTest(template = "room")
    public static void theBarrelFermentsOnlyShut(GameTestHelper helper) {
        StationTests.theBarrelFermentsOnlyShut(helper);
    }

    @GameTest(template = "room")
    public static void everythingInTheBarrelFermentsAtOnce(GameTestHelper helper) {
        StationTests.everythingInTheBarrelFermentsAtOnce(helper);
    }

    @GameTest(template = "room")
    public static void theJuiceComesBackIntoBottles(GameTestHelper helper) {
        StationTests.theJuiceComesBackIntoBottles(helper);
    }

    @GameTest(template = "room")
    public static void theWineIsDrawnAtTheBung(GameTestHelper helper) {
        StationTests.theWineIsDrawnAtTheBung(helper);
    }

    @GameTest(template = "room")
    public static void theStationsDataStaysSmall(GameTestHelper helper) {
        StationTests.theStationsDataStaysSmall(helper);
    }

    @GameTest(template = "empty")
    public static void theBarrelsSceneIsRead(GameTestHelper helper) {
        StationTests.theBarrelsSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void thePotIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        PotTests.thePotIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = "room")
    public static void thePotsSlotsAreItsOwn(GameTestHelper helper) {
        PotTests.thePotsSlotsAreItsOwn(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void aStirredPotCooksAndAnUnstirredOneWaits(GameTestHelper helper) {
        PotTests.aStirredPotCooksAndAnUnstirredOneWaits(helper);
    }

    @GameTest(template = "room")
    public static void theSoupIsFilledIntoBowls(GameTestHelper helper) {
        PotTests.theSoupIsFilledIntoBowls(helper);
    }

    @GameTest(template = "room")
    public static void unevenStacksMakeNoServingsFromNothing(GameTestHelper helper) {
        PotTests.unevenStacksMakeNoServingsFromNothing(helper);
    }

    @GameTest(template = "room")
    public static void whatAnIngredientLeavesGoesToTheCook(GameTestHelper helper) {
        PotTests.whatAnIngredientLeavesGoesToTheCook(helper);
    }

    @GameTest(template = "room", timeoutTicks = 400)
    public static void starsGiveBonusServings(GameTestHelper helper) {
        PotTests.starsGiveBonusServings(helper);
    }

    @GameTest(template = "room", timeoutTicks = PotTests.OWN_TIMEOUT)
    public static void aHopperFedPotCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksUpstreamsWay(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void aHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void aCookInTheMenuCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aCookInTheMenuCooksUpstreamsWay(helper);
    }

    @GameTest(template = "room", timeoutTicks = 300)
    public static void aPotLeftMidStirGoesBackToUpstreamsTick(GameTestHelper helper) {
        PotTests.aPotLeftMidStirGoesBackToUpstreamsTick(helper);
    }

    @GameTest(template = "room")
    public static void aDishThatNeedsNoBowlComesOutWhole(GameTestHelper helper) {
        PotTests.aDishThatNeedsNoBowlComesOutWhole(helper);
    }

    @GameTest(template = "room")
    public static void twoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper);
    }

    @GameTest(template = "room", timeoutTicks = 600)
    public static void everyDishIsLaidFromTheBookAndCooks(GameTestHelper helper) {
        PotTests.everyDishIsLaidFromTheBookAndCooks(helper);
    }

    @GameTest(template = "room")
    public static void aLearnedDishCooksForTheCookWhoLearnedIt(GameTestHelper helper) {
        PotTests.aLearnedDishCooksForTheCookWhoLearnedIt(helper);
    }

    @GameTest(template = "room")
    public static void thePotTellsItsClientsWhatItHolds(GameTestHelper helper) {
        PotTests.thePotTellsItsClientsWhatItHolds(helper);
    }

    @GameTest(template = "room")
    public static void thePotsDataStaysSmall(GameTestHelper helper) {
        PotTests.thePotsDataStaysSmall(helper);
    }

    @GameTest(template = "empty")
    public static void thePotsSceneIsRead(GameTestHelper helper) {
        PotTests.thePotsSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void candlelightsPotIsAStationOnlyWithCandlelight(GameTestHelper helper) {
        PotTests.thePotIsAStationOnlyWithFarmAndCharm(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightThePotsSlotsAreItsOwn(GameTestHelper helper) {
        PotTests.thePotsSlotsAreItsOwn(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void candlelightAStirredPotCooksAndAnUnstirredOneWaits(GameTestHelper helper) {
        PotTests.aStirredPotCooksAndAnUnstirredOneWaits(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightTheSoupIsFilledIntoBowls(GameTestHelper helper) {
        PotTests.theSoupIsFilledIntoBowls(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightUnevenStacksMakeNoServingsFromNothing(GameTestHelper helper) {
        PotTests.unevenStacksMakeNoServingsFromNothing(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightWhatAnIngredientLeavesGoesToTheCook(GameTestHelper helper) {
        PotTests.whatAnIngredientLeavesGoesToTheCook(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 400)
    public static void candlelightStarsGiveBonusServings(GameTestHelper helper) {
        PotTests.starsGiveBonusServings(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = PotTests.OWN_TIMEOUT)
    public static void candlelightAHopperFedPotCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aHopperFedPotCooksUpstreamsWay(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void candlelightAHopperFeedsThePotByItselfBesideACook(GameTestHelper helper) {
        PotTests.aHopperFeedsThePotByItselfBesideACook(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void candlelightACookInTheMenuCooksUpstreamsWay(GameTestHelper helper) {
        PotTests.aCookInTheMenuCooksUpstreamsWay(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 300)
    public static void candlelightAPotLeftMidStirGoesBackToUpstreamsTick(GameTestHelper helper) {
        PotTests.aPotLeftMidStirGoesBackToUpstreamsTick(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightADishThatNeedsNoBowlComesOutWhole(GameTestHelper helper) {
        PotTests.aDishThatNeedsNoBowlComesOutWhole(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightTwoAlikeThrownInMakeTheirDish(GameTestHelper helper) {
        PotTests.twoAlikeThrownInMakeTheirDish(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room", timeoutTicks = 600)
    public static void candlelightEveryDishIsLaidFromTheBookAndCooks(GameTestHelper helper) {
        PotTests.everyDishIsLaidFromTheBookAndCooks(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightALearnedDishCooksForTheCookWhoLearnedIt(GameTestHelper helper) {
        PotTests.aLearnedDishCooksForTheCookWhoLearnedIt(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightThePotTellsItsClientsWhatItHolds(GameTestHelper helper) {
        PotTests.thePotTellsItsClientsWhatItHolds(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void candlelightThePotsDataStaysSmall(GameTestHelper helper) {
        PotTests.thePotsDataStaysSmall(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "empty")
    public static void candlelightThePotsSceneIsRead(GameTestHelper helper) {
        PotTests.thePotsSceneIsRead(helper, PotTests.CANDLELIGHT);
    }

    @GameTest(template = "room")
    public static void theStoveIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        StoveTests.theStoveIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = "room")
    public static void theStovesSlotsAreItsOwn(GameTestHelper helper) {
        StoveTests.theStovesSlotsAreItsOwn(helper);
    }

    @GameTest(template = "room", timeoutTicks = 500)
    public static void breadThroughTheStationIsBreadByHoppers(GameTestHelper helper) {
        StoveTests.breadThroughTheStationIsBreadByHoppers(helper);
    }

    @GameTest(template = "room")
    public static void takingTheDishKeepsWhatBakes(GameTestHelper helper) {
        StoveTests.takingTheDishKeepsWhatBakes(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void theStoveBakesOnlyShut(GameTestHelper helper) {
        StoveTests.theStoveBakesOnlyShut(helper);
    }

    @GameTest(template = "room")
    public static void fuelGoesOnTheFireWithTheDoorShut(GameTestHelper helper) {
        StoveTests.fuelGoesOnTheFireWithTheDoorShut(helper);
    }

    @GameTest(template = "room", timeoutTicks = StoveTests.THREE_BAKES)
    public static void twoAlikeBakeToTheLastSet(GameTestHelper helper) {
        StoveTests.twoAlikeBakeToTheLastSet(helper);
    }

    @GameTest(template = "room", timeoutTicks = 600)
    public static void everyBakingIsLaidFromTheBookAndBakes(GameTestHelper helper) {
        StoveTests.everyBakingIsLaidFromTheBookAndBakes(helper);
    }

    @GameTest(template = "room")
    public static void aClientsCopyOfAnEmptiedStoveIsEmpty(GameTestHelper helper) {
        StoveTests.aClientsCopyOfAnEmptiedStoveIsEmpty(helper);
    }

    @GameTest(template = "room")
    public static void theStovesDataStaysSmall(GameTestHelper helper) {
        StoveTests.theStovesDataStaysSmall(helper);
    }

    @GameTest(template = "empty")
    public static void theStovesSceneIsRead(GameTestHelper helper) {
        StoveTests.theStovesSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void theRoasterIsAStationOnlyWithFarmAndCharm(GameTestHelper helper) {
        RoasterTests.theRoasterIsAStationOnlyWithFarmAndCharm(helper);
    }

    @GameTest(template = "room")
    public static void aSwirledRoasterRoastsAndAnUnswirledOneWaits(GameTestHelper helper) {
        RoasterTests.aSwirledRoasterRoastsAndAnUnswirledOneWaits(helper);
    }

    @GameTest(template = "room", timeoutTicks = PotTests.OWN_TIMEOUT)
    public static void aHopperFedRoasterRoastsUpstreamsWay(GameTestHelper helper) {
        RoasterTests.aHopperFedRoasterRoastsUpstreamsWay(helper);
    }

    @GameTest(template = "room", timeoutTicks = 600)
    public static void everyRoastIsLaidFromTheBookAndRoasts(GameTestHelper helper) {
        RoasterTests.everyRoastIsLaidFromTheBookAndRoasts(helper);
    }

    @GameTest(template = "empty")
    public static void theRoastersSceneIsRead(GameTestHelper helper) {
        RoasterTests.theRoastersSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void theKettleIsAStationOnlyWithHerbalBrews(GameTestHelper helper) {
        KettleTests.theKettleIsAStationOnlyWithHerbalBrews(helper);
    }

    @GameTest(template = "room")
    public static void theKettlesSlotsAreItsOwn(GameTestHelper helper) {
        KettleTests.theKettlesSlotsAreItsOwn(helper);
    }

    @GameTest(template = "room")
    public static void waterIsPouredByTheKettleAndTheBucketComesBack(GameTestHelper helper) {
        KettleTests.waterIsPouredByTheKettleAndTheBucketComesBack(helper);
    }

    @GameTest(template = "room")
    public static void whatWasLeftInTheWaterSlotGoesToWhoeverPoursNext(GameTestHelper helper) {
        KettleTests.whatWasLeftInTheWaterSlotGoesToWhoeverPoursNext(helper);
    }

    @GameTest(template = "room")
    public static void aTeaBrewsOnlyWithWaterAndHeatEnough(GameTestHelper helper) {
        KettleTests.aTeaBrewsOnlyWithWaterAndHeatEnough(helper);
    }

    @GameTest(template = "room")
    public static void aKettleBrewsIntoItselfAndIsPouredAtTheSpout(GameTestHelper helper) {
        KettleTests.aKettleBrewsIntoItselfAndIsPouredAtTheSpout(helper);
    }

    @GameTest(template = "room", timeoutTicks = 400)
    public static void everyTeaIsLaidFromTheBookAndBrews(GameTestHelper helper) {
        KettleTests.everyTeaIsLaidFromTheBookAndBrews(helper);
    }

    @GameTest(template = "empty")
    public static void theKettlesSceneIsRead(GameTestHelper helper) {
        KettleTests.theKettlesSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void thePressIsAStationOnlyWithVinery(GameTestHelper helper) {
        PressTests.thePressIsAStationOnlyWithVinery(helper);
    }

    @GameTest(template = "room")
    public static void thePressesSlotsAreItsOwn(GameTestHelper helper) {
        PressTests.thePressesSlotsAreItsOwn(helper);
    }

    @GameTest(template = "room")
    public static void anAppleLaidAtTheStationIsPressedAndBottled(GameTestHelper helper) {
        PressTests.anAppleLaidAtTheStationIsPressedAndBottled(helper);
    }

    @GameTest(template = "room")
    public static void thePressWaitsWhileItsBasketIsOpen(GameTestHelper helper) {
        PressTests.thePressWaitsWhileItsBasketIsOpen(helper);
    }

    @GameTest(template = "room")
    public static void mashLaidByHandIsNoPressing(GameTestHelper helper) {
        PressTests.mashLaidByHandIsNoPressing(helper);
    }

    @GameTest(template = "room")
    public static void everythingThePressBottlesIsLaidFromTheBook(GameTestHelper helper) {
        PressTests.everythingThePressBottlesIsLaidFromTheBook(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void anAppleRunsIntoTheTubAndIsBottledThere(GameTestHelper helper) {
        PressTests.anAppleRunsIntoTheTubAndIsBottledThere(helper);
    }

    @GameTest(template = "room")
    public static void aClickFillsEveryBottleTheTubHolds(GameTestHelper helper) {
        PressTests.aClickFillsEveryBottleTheTubHolds(helper);
    }

    @GameTest(template = "empty")
    public static void thePressesSceneIsRead(GameTestHelper helper) {
        PressTests.thePressesSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void theMincerIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        MincerTests.theMincerIsAStationOnlyWithFarmCharm(helper);
    }

    @GameTest(template = "room")
    public static void aLoadOfUpToEightTakesTwoTurns(GameTestHelper helper) {
        MincerTests.aLoadOfUpToEightTakesTwoTurns(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void nothingIsMincedWithoutTheHand(GameTestHelper helper) {
        MincerTests.nothingIsMincedWithoutTheHand(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void whatIsMincedWaitsUnderTheSpout(GameTestHelper helper) {
        MincerTests.whatIsMincedWaitsUnderTheSpout(helper);
    }

    @GameTest(template = "room")
    public static void whatDoesNotFitGoesToTheCook(GameTestHelper helper) {
        MincerTests.whatDoesNotFitGoesToTheCook(helper);
    }

    @GameTest(template = "room")
    public static void everythingTheMincerMakesIsLaidFromTheBook(GameTestHelper helper) {
        MincerTests.everythingTheMincerMakesIsLaidFromTheBook(helper);
    }

    @GameTest(template = "empty")
    public static void theMincersSceneIsRead(GameTestHelper helper) {
        MincerTests.theMincersSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void aHeavyLoadTurnsSlowly(GameTestHelper helper) {
        MincerTests.aHeavyLoadTurnsSlowly(helper);
    }

    @GameTest(template = "room")
    public static void theBowlIsAStationOnlyWithFarmCharm(GameTestHelper helper) {
        BowlTests.theBowlIsAStationOnlyWithFarmCharm(helper);
    }

    @GameTest(template = "room")
    public static void aBowlfulIsMixedByItsCircles(GameTestHelper helper) {
        BowlTests.aBowlfulIsMixedByItsCircles(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void nothingIsMixedWithoutTheHand(GameTestHelper helper) {
        BowlTests.nothingIsMixedWithoutTheHand(helper);
    }

    @GameTest(template = "room")
    public static void everythingTheBowlMakesIsLaidFromTheBook(GameTestHelper helper) {
        BowlTests.everythingTheBowlMakesIsLaidFromTheBook(helper);
    }

    @GameTest(template = "empty")
    public static void theBowlsSceneIsRead(GameTestHelper helper) {
        BowlTests.theBowlsSceneIsRead(helper);
    }

    @GameTest(template = "room")
    public static void whatTheBowlsIngredientsLeaveGoesToTheCook(GameTestHelper helper) {
        BowlTests.whatTheBowlsIngredientsLeaveGoesToTheCook(helper);
    }

    @GameTest(template = "room", timeoutTicks = 1000)
    public static void aFigureOfEightMixesAsCirclesDo(GameTestHelper helper) {
        BowlTests.aFigureOfEightMixesAsCirclesDo(helper);
    }

    @GameTest(template = "room")
    public static void theWhiskGoesNoFasterThanTheMixtureLets(GameTestHelper helper) {
        BowlTests.theWhiskGoesNoFasterThanTheMixtureLets(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void aThickBowlfulCountsAsFastAsAThinOne(GameTestHelper helper) {
        BowlTests.aThickBowlfulCountsAsFastAsAThinOne(helper);
    }

    @GameTest(template = "room")
    public static void theResultIsTakenFromTheBadge(GameTestHelper helper) {
        BowlTests.theResultIsTakenFromTheBadge(helper);
    }

    @GameTest(template = "room")
    public static void aPouredLiquidIsDrawnBackByItsContainer(GameTestHelper helper) {
        BowlTests.aPouredLiquidIsDrawnBackByItsContainer(helper);
    }

    @GameTest(template = "room")
    public static void aLoadOfUpToEightIsPressedByFourPresses(GameTestHelper helper) {
        PressTests.aLoadOfUpToEightIsPressedByFourPresses(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void nothingIsPressedWithoutTheHand(GameTestHelper helper) {
        PressTests.nothingIsPressedWithoutTheHand(helper);
    }

    @GameTest(template = "room", timeoutTicks = 200)
    public static void theJuiceOfALoadRunsQuickly(GameTestHelper helper) {
        PressTests.theJuiceOfALoadRunsQuickly(helper);
    }
}
