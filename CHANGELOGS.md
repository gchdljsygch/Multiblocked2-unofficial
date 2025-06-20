# ChangeLog

## v1.0.30.a
* Fixed World Preview renderer doesn't work correctly with forge model

## v1.0.30
* Fixed Forge Additional Capabilities doesn't work
* Added Light Condition for `sky light`, `block light` and `can see sky`
* Added Consume Inputs After Working (furnace-like machine)
* Added ME Interface Trait

## v1.0.29
* Fixed Mek EMI support
* Fixed Photon node crash
* Added an option for whether the block is `collisionShapeFullBlock`
* Added machine translated ru_ru lang file (thanks to @серый калчедан)
* Added fluid auto world io

## v1.0.28.b
* Fixes the pattern lock won't release.

## v1.0.28.a
* Fixes the pattern lock won't release.

## v1.0.28
* Added Create Trait UI to display the rpm and stress of the machine
* Added Trait Capability IO Override (thanks to @Tcat2000)
* Fixed crash while the ui name contains bracket-like characters
* Fixed PNC Proxy Block crash
* Improved the Tag Ingredient Copy

## v1.0.27
* Fixed Tank Widget GUI IO reversed
* Fixed disable recipe logic settings doesn't work
* Fixed After Recipe Working Event also triggers when structure is destroyed
* Fixed Multiblock proxy parts don't export pressure
* Added try-catch to prevent crash when trying to execute kjs scripts
* Added TransferProxyRecipeEvent to allow customizing transfer proxy recipe behavior
* Added MBDRecipeType#recipeBuilder to build dynamic recipe as kjs recipe event
* Added supports that the part can display its controller trait's ui and the controller can display its part's ui
* Bump up photon version, and fix photon version compatibility
