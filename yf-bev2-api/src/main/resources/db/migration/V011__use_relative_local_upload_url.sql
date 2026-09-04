-- Local files must be served by the current API host. A relative URL avoids
-- pointing browsers at the legacy be2.yfhl.net domain or at localhost on a
-- different client machine.
UPDATE `pl_plugin_data`
SET `config_data` = JSON_SET(`config_data`, '$.visitUrl', '')
WHERE `code` = 'upload-local'
  AND JSON_VALID(`config_data`);
