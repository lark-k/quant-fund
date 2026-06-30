from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="QUANT_ENGINE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8091
    rule_model_version: str = "rule-v1.19.0"
    default_deadline: str = "15:00:00"
    log_level: str = "INFO"
    workers: int = 1
    backtest_workers: int = 6
    max_batch_funds: int = 500
    max_param_grid: int = 100
    cache_dir: str = ".cache"
    enable_parquet_cache: bool = True
    lgbm_n_jobs: int = 8


@lru_cache
def get_settings() -> Settings:
    return Settings()
