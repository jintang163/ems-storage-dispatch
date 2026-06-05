import os
import json
import time
import logging
from typing import List, Dict, Any
from threading import Thread, Lock

logger = logging.getLogger(__name__)


class CacheManager:
    def __init__(self, cache_path: str = './data/cache',
                 max_file_size: int = 104857600,
                 retry_interval: int = 60,
                 enabled: bool = True):
        self.cache_path = cache_path
        self.max_file_size = max_file_size
        self.retry_interval = retry_interval
        self.enabled = enabled
        self.lock = Lock()
        self._current_file = None
        self._retry_thread = None
        self._running = False

        if self.enabled:
            os.makedirs(cache_path, exist_ok=True)
            self._get_current_file()

    def _get_current_file(self) -> str:
        date_str = time.strftime('%Y%m%d')
        filename = f"cache_{date_str}.jsonl"
        filepath = os.path.join(self.cache_path, filename)

        if self._current_file != filepath:
            self._current_file = filepath

        return filepath

    def cache_data(self, topic: str, payload: Dict[str, Any]) -> bool:
        if not self.enabled:
            return False

        try:
            cache_entry = {
                'topic': topic,
                'payload': payload,
                'timestamp': int(time.time() * 1000)
            }

            with self.lock:
                filepath = self._get_current_file()

                if os.path.exists(filepath) and os.path.getsize(filepath) > self.max_file_size:
                    backup_file = f"{filepath}.bak"
                    if os.path.exists(backup_file):
                        os.remove(backup_file)
                    os.rename(filepath, backup_file)
                    logger.warning(f"Cache file exceeded max size, rotated to {backup_file}")

                with open(filepath, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(cache_entry, ensure_ascii=False) + '\n')

            logger.debug(f"Cached data for topic: {topic}")
            return True
        except Exception as e:
            logger.error(f"Failed to cache data: {e}")
            return False

    def get_cached_data(self, limit: int = 1000) -> List[Dict[str, Any]]:
        if not self.enabled:
            return []

        data = []
        try:
            with self.lock:
                filepath = self._get_current_file()
                if not os.path.exists(filepath):
                    return data

                with open(filepath, 'r', encoding='utf-8') as f:
                    for i, line in enumerate(f):
                        if i >= limit:
                            break
                        try:
                            entry = json.loads(line.strip())
                            data.append(entry)
                        except json.JSONDecodeError:
                            continue

            return data
        except Exception as e:
            logger.error(f"Failed to read cached data: {e}")
            return data

    def remove_cached_data(self, entries: List[Dict[str, Any]]) -> bool:
        if not self.enabled or not entries:
            return False

        temp_file = None
        try:
            timestamps_to_remove = {entry['timestamp'] for entry in entries}

            with self.lock:
                filepath = self._get_current_file()
                if not os.path.exists(filepath):
                    return True

                temp_file = f"{filepath}.tmp"
                removed_count = 0

                with open(filepath, 'r', encoding='utf-8') as f_in, \
                     open(temp_file, 'w', encoding='utf-8') as f_out:
                    for line in f_in:
                        try:
                            entry = json.loads(line.strip())
                            if entry.get('timestamp') not in timestamps_to_remove:
                                f_out.write(line)
                            else:
                                removed_count += 1
                        except json.JSONDecodeError:
                            f_out.write(line)

                os.replace(temp_file, filepath)
                logger.debug(f"Removed {removed_count} cached entries")
                return True
        except Exception as e:
            logger.error(f"Failed to remove cached data: {e}")
            if temp_file and os.path.exists(temp_file):
                os.remove(temp_file)
            return False

    def start(self):
        if not self.enabled or self._retry_thread:
            return

        self._running = True
        self._retry_thread = Thread(
            target=self._retry_loop,
            args=(self._retry_publish,),
            daemon=True
        )
        self._retry_thread.start()
        logger.info("Cache retry thread started")

    def stop(self):
        self._running = False
        if self._retry_thread:
            self._retry_thread.join(timeout=5)
            self._retry_thread = None
        logger.info("Cache retry thread stopped")

    def start_retry_thread(self, publish_callback):
        self._retry_publish = publish_callback
        self.start()

    def stop_retry_thread(self):
        self.stop()

    def set_publish_callback(self, publish_callback):
        self._retry_publish = publish_callback

    def _retry_loop(self, publish_callback=None):
        while self._running:
            try:
                callback = publish_callback if publish_callback else getattr(self, '_retry_publish', None)
                if not callback:
                    time.sleep(self.retry_interval)
                    continue

                cached_data = self.get_cached_data()
                if cached_data:
                    logger.info(f"Found {len(cached_data)} cached entries to retry")
                    success_entries = []

                    for entry in cached_data:
                        try:
                            callback(entry['topic'], entry['payload'])
                            success_entries.append(entry)
                        except Exception as e:
                            logger.warning(f"Failed to retry cached entry: {e}")
                            break

                    if success_entries:
                        self.remove_cached_data(success_entries)
                        logger.info(f"Successfully retried {len(success_entries)} cached entries")

            except Exception as e:
                logger.error(f"Error in retry loop: {e}")

            time.sleep(self.retry_interval)

    def get_cache_stats(self) -> Dict[str, Any]:
        if not self.enabled:
            return {'enabled': False}

        stats = {
            'enabled': True,
            'cache_path': self.cache_path,
            'files': []
        }

        try:
            for filename in os.listdir(self.cache_path):
                filepath = os.path.join(self.cache_path, filename)
                if os.path.isfile(filepath):
                    stats['files'].append({
                        'name': filename,
                        'size': os.path.getsize(filepath),
                        'modified': os.path.getmtime(filepath)
                    })

                    line_count = 0
                    with open(filepath, 'r', encoding='utf-8') as f:
                        for _ in f:
                            line_count += 1
                    stats['files'][-1]['entries'] = line_count

        except Exception as e:
            stats['error'] = str(e)

        return stats
