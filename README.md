# DeluxeAuctions

A configurable auction-house plugin for Spigot, Paper and Folia servers,
inspired by the auction houses found on popular skyblock servers.
Players list items for sale or for bidding, browse open auctions, and
collect their winnings or expired listings.

## Features

- Buy-it-now and bidding auctions
- Auction expiry and collection box
- Category and search GUIs, fully configurable
- Per-player auction and bid limits, taxes
- Folia support
- Vault economy support, with optional alternative economies
  (CoinsEngine, RoyaleEconomy, PlayerPoints, TokenManager, UltraEconomy, etc.)
- PlaceholderAPI support
- HeadDatabase, EcoItems, libreforge / eco item support
- Optional integrations: LiteBans, AdvancedBan, Lands, Skript
- Optional Redis sync (DeluxeAuctionsRedis) and display addon
  (DeluxeAuctionsDisplay)
- Loads before legacy auction plugins (AuctionMaster, zAuctionHouseV3)
  so they don't conflict

## Requirements

- Spigot / Paper / Folia (built against API 1.13)
- Java 8 or newer
- Optional integrations (soft dependencies): Vault, CoinsEngine,
  HeadDatabase, RoyaleEconomy, PlaceholderAPI, PlayerPoints, TokenManager,
  Lands, UltraEconomy, LiteBans, AdvancedBan, EcoItems, eco, libreforge,
  AuctionMaster, zAuctionHouseV3, DeluxeAuctionsRedis,
  DeluxeAuctionsDisplay, EdPrison, Skript

## Commands

- `/auction` (aliases: `/deluxeauctions`, `/ah`, `/auc`) — open the auction house
- `/auctionadmin` (aliases: `/deluxeauctionsadmin`, `/ahadmin`, `/aucadmin`) — admin tools

## Installation

1. Drop `DeluxeAuctions.jar` into your server's `plugins/` folder.
2. Start the server once to generate the default configuration.
3. Edit the files in `plugins/DeluxeAuctions/`, then reload or restart.

## Configuration

The plugin generates YAML files under `plugins/DeluxeAuctions/` for
auction settings, GUI layouts, messages, economy and integrations.
Listing duration, price ranges, taxes, sounds, and category filters are
all driven from these files.

## License

See `LICENSE`.
