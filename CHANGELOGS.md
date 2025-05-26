# ChangeLog

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
