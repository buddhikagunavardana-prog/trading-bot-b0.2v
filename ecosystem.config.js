// ==============================================================================
// PM2 Process Manager Ecosystem Configuration
// ==============================================================================

module.exports = {
  apps: [
    {
      name: 'trading-engine-service',
      script: './deploy/server.js',
      instances: 'max',
      exec_mode: 'cluster',
      autorestart: true,
      watch: false,
      max_memory_restart: '1G',
      exp_backoff_restart_delay: 100,
      env_production: {
        NODE_ENV: 'production',
        PORT: 8080,
        LOG_LEVEL: 'info',
      },
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
      error_file: './logs/pm2-error.log',
      out_file: './logs/pm2-out.log',
      merge_logs: true,
      kill_timeout: 5000,
      listen_timeout: 8000
    }
  ]
};
