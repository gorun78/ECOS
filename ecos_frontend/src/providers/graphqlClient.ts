import { ApolloClient, InMemoryCache, createHttpLink } from '@apollo/client';
import { API_BASE } from '../services/httpClient';

const graphqlClient = new ApolloClient({
  link: createHttpLink({
    uri: `${API_BASE}/graphql`,
  }),
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: {
      fetchPolicy: 'cache-and-network',
    },
    query: {
      fetchPolicy: 'network-only',
    },
  },
});

export { graphqlClient };
