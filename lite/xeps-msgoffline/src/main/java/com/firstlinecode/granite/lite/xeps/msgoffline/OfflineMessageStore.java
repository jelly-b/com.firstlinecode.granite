package com.firstlinecode.granite.lite.xeps.msgoffline;

import java.util.Iterator;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.firstlinecode.basalt.protocol.core.JabberId;
import com.firstlinecode.granite.framework.core.config.IConfiguration;
import com.firstlinecode.granite.framework.core.config.IConfigurationAware;
import com.firstlinecode.granite.framework.core.supports.data.IDataObjectFactory;
import com.firstlinecode.granite.framework.core.supports.data.IDataObjectFactoryAware;
import com.firstlinecode.granite.framework.im.IOfflineMessageStore;
import com.firstlinecode.granite.framework.im.OfflineMessage;
import com.firstlinecode.granite.framework.supports.mybatis.DataObjectIterator;

@Transactional
@Component
public class OfflineMessageStore implements IOfflineMessageStore, IDataObjectFactoryAware,
			IConfigurationAware {
	private static final String CONFIGURATION_KEY_OFFLINE_MESSAGES_FETCH_SIZE = "offline.messages.fetch.size";
	private static final int DEFAULT_FETCH_SIZE = 20;
	
	private IDataObjectFactory dataObjectFactory;
	private int offlineMessagesFetchSize = DEFAULT_FETCH_SIZE;
	
	@Autowired
	private SqlSession sqlSession;
	
	@Override
	public void save(JabberId jid, String messageId, String message) {
		OfflineMessage offlineMessage = dataObjectFactory.create(OfflineMessage.class);
		offlineMessage.setMessageId(messageId);
		offlineMessage.setMessage(message);
		offlineMessage.setJid(jid);
		
		getMapper().insert(offlineMessage);
	}
	
	private OfflineMessageMapper getMapper() {
		return sqlSession.getMapper(OfflineMessageMapper.class);
	}

	@Override
	public OfflineMessage get(JabberId jid, String messageId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void remove(JabberId jid, String messageId) {
		getMapper().deleteByJidAndMessageId(jid, messageId);
	}

	@Override
	public Iterator<OfflineMessage> iterator(final JabberId jid) {
		return new DataObjectIterator<OfflineMessage>(offlineMessagesFetchSize) {
			@Override
			protected List<OfflineMessage> doFetch(int offset, int limit) {
				return getMapper().selectByJid(jid, limit, offset);
			}
		};
	}
	
	@Override
	public boolean isEmpty(JabberId jid) {
		return getSize(jid) == 0;
	}

	@Override
	public int getSize(JabberId jid) {
		return getMapper().selectCountByJid(jid);
	}

	@Override
	public void setDataObjectFactory(IDataObjectFactory dataObjectFactory) {
		this.dataObjectFactory = dataObjectFactory;
	}

	@Override
	public void setConfiguration(IConfiguration configuration) {
		offlineMessagesFetchSize = configuration.getInteger(CONFIGURATION_KEY_OFFLINE_MESSAGES_FETCH_SIZE,
				DEFAULT_FETCH_SIZE);
	}

}
